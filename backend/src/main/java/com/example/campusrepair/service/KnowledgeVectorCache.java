package com.example.campusrepair.service;

import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.entity.KnowledgeBase;
import com.example.campusrepair.repository.KnowledgeBaseRepository;
import com.example.campusrepair.util.CosineSimilarity;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 维修知识库内存向量缓存
 *
 * @Description 启动时全量读 knowledge_base（已上架 + 未删除），调硅基流动 Embedding
 *              把"关键词 + 标准问题"转成向量，全部载入内存 List。
 *              检索时对当前问题做 Embedding，内存算余弦相似度取 Top3 并过滤阈值；
 *              Embedding 异常（或缓存为空、向量检索无命中）降级为 MySQL keywords LIKE。
 *              不引入 Milvus/Chroma 等额外向量库。
 * @Author 闵娟
 * @Date 2026-11-24
 * @Version 1.0
 */
@Slf4j
@Service
public class KnowledgeVectorCache {

    private static final int TOP_K = 3;

    /** LIKE 降级时的口语同义词：学生不会用"忽冷忽热"这种标准词 */
    private static final Map<String, List<String>> COLLOQUIAL_KEYWORDS;

    static {
        // 顺序有意义：先按症状命中，再按设备命中，避免"热水器"把"不加热"顶成 Top-1
        Map<String, List<String>> synonyms = new LinkedHashMap<>();
        synonyms.put("忽上忽下", List.of("忽冷忽热", "水温"));
        synonyms.put("忽高忽低", List.of("忽冷忽热", "水温"));
        synonyms.put("一会烫", List.of("忽冷忽热", "水温"));
        synonyms.put("一会儿烫", List.of("忽冷忽热", "水温"));
        synonyms.put("一会凉", List.of("忽冷忽热", "水温"));
        synonyms.put("一会儿凉", List.of("忽冷忽热", "水温"));
        synonyms.put("冷热", List.of("忽冷忽热"));
        synonyms.put("洗澡", List.of("热水器", "水温"));
        synonyms.put("淋浴", List.of("淋浴", "水温"));
        COLLOQUIAL_KEYWORDS = Collections.unmodifiableMap(synonyms);
    }

    /** 低于此相似度不进 Top3 */
    @Value("${rag.min-similarity:0.6}")
    private double minSimilarity;

    private final KnowledgeBaseRepository repository;
    private final EmbeddingService embeddingService;

    /** copy-on-write：刷新时整体替换，检索线程只读，无需加锁 */
    private volatile List<VectorEntry> cache = List.of();

    private record VectorEntry(KnowledgeBase kb, float[] vector) {
    }

    public KnowledgeVectorCache(KnowledgeBaseRepository repository,
                                EmbeddingService embeddingService) {
        this.repository = repository;
        this.embeddingService = embeddingService;
    }

    /** 启动即加载；任何失败只记 WARN，缓存为空时自动走 LIKE，不让启动失败 */
    @PostConstruct
    public void refresh() {
        try {
            List<KnowledgeBase> rows = repository.browseApproved(null);
            if (rows.isEmpty()) {
                cache = List.of();
                log.info("知识库缓存刷新完成：0 条（检索将走 LIKE 降级）");
                return;
            }
            List<String> texts = rows.stream()
                    .map(kb -> safe(kb.getKeyword()) + " " + safe(kb.getQuestion()))
                    .toList();
            List<Optional<float[]>> vectors = embeddingService.embedBatch(texts);
            List<VectorEntry> loaded = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                Optional<float[]> vector = i < vectors.size() ? vectors.get(i) : Optional.empty();
                if (vector.isPresent()) {
                    loaded.add(new VectorEntry(rows.get(i), vector.get()));
                }
            }
            cache = List.copyOf(loaded);
            log.info("知识库缓存刷新完成：共 {} 条，其中 {} 条已向量化", rows.size(), loaded.size());
        } catch (Exception e) {
            cache = List.of();
            log.warn("知识库缓存刷新失败，检索将走 LIKE 降级：{}", e.getMessage());
        }
    }

    /** 当前缓存条目数（手动刷新接口回显用） */
    public int cachedSize() {
        return cache.size();
    }

    /** 检索 Top3：向量余弦优先，异常/无命中降级 LIKE */
    public List<DiagnosisResult.Match> search(String question) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        List<VectorEntry> snapshot = cache;
        if (!snapshot.isEmpty()) {
            Optional<float[]> queryVector = embeddingService.embed(q);
            if (queryVector.isPresent()) {
                List<Scored> scored = new ArrayList<>();
                for (VectorEntry entry : snapshot) {
                    double similarity = CosineSimilarity.of(queryVector.get(), entry.vector());
                    scored.add(new Scored(entry.kb(), similarity));
                }
                scored.sort(Comparator.comparingDouble(Scored::similarity).reversed());
                List<DiagnosisResult.Match> hits = scored.stream()
                        .filter(s -> s.similarity() >= minSimilarity)
                        .limit(TOP_K)
                        .map(s -> toMatch(s.kb(), s.similarity()))
                        .toList();
                if (!hits.isEmpty()) {
                    return hits;
                }
            }
        }
        return likeSearch(q);
    }

    private record Scored(KnowledgeBase kb, double similarity) {
    }

    /** MySQL keywords LIKE 降级：原文 → 口语同义词 → 种子关键词反查 */
    private List<DiagnosisResult.Match> likeSearch(String q) {
        List<DiagnosisResult.Match> direct = searchMatches(q);
        if (!direct.isEmpty()) {
            return direct.stream().limit(TOP_K).toList();
        }
        String lower = q.toLowerCase();
        Map<Long, DiagnosisResult.Match> found = new LinkedHashMap<>();
        COLLOQUIAL_KEYWORDS.forEach((phrase, keywords) -> {
            if (lower.contains(phrase)) {
                for (String keyword : keywords) {
                    for (DiagnosisResult.Match m : searchMatches(keyword)) {
                        found.putIfAbsent(m.getId(), m);
                    }
                }
            }
        });
        if (!found.isEmpty()) {
            return found.values().stream().limit(TOP_K).toList();
        }
        return keywordReverseSearch(q);
    }

    /** 用条目自己的关键词反查问题文本，命中数多的排前面（短句兜底） */
    private List<DiagnosisResult.Match> keywordReverseSearch(String q) {
        String lower = q.toLowerCase();
        return repository.browseApproved(null).stream()
                .map(kb -> Map.entry(kb, countKeywordHits(kb.getKeyword(), lower)))
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator
                        .<Map.Entry<KnowledgeBase, Integer>>comparingInt(Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().getHelpful() == null
                                ? 0L : entry.getKey().getHelpful().longValue(), Comparator.reverseOrder()))
                .limit(TOP_K)
                .map(entry -> toMatch(entry.getKey(), null))
                .toList();
    }

    private int countKeywordHits(String keywordField, String lowerQuestion) {
        if (keywordField == null || keywordField.isBlank()) {
            return 0;
        }
        int hits = 0;
        for (String token : keywordField.split("[,，、\\s]+")) {
            String normalized = token.trim().toLowerCase();
            if (normalized.length() >= 2 && lowerQuestion.contains(normalized)) {
                hits++;
            }
        }
        return hits;
    }

    private List<DiagnosisResult.Match> searchMatches(String query) {
        return repository.searchApproved(query).stream()
                .map(kb -> toMatch(kb, null))
                .toList();
    }

    private DiagnosisResult.Match toMatch(KnowledgeBase kb, Double similarity) {
        DiagnosisResult.Match match = new DiagnosisResult.Match();
        match.setId(kb.getId());
        match.setTitle(kb.getTitle());
        match.setQuestion(kb.getQuestion());
        match.setAnswer(kb.getAnswer());
        match.setHelpful(kb.getHelpful() == null ? 0L : kb.getHelpful().longValue());
        match.setCategoryId(kb.getCategoryId());
        match.setSimilarity(similarity);
        return match;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}
