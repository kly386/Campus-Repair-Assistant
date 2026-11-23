package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.dto.KnowledgeSaveRequest;
import com.example.campusrepair.entity.DiagnosisSession;
import com.example.campusrepair.entity.KnowledgeBase;
import com.example.campusrepair.entity.KnowledgeHistory;
import com.example.campusrepair.repository.DiagnosisSessionRepository;
import com.example.campusrepair.repository.KnowledgeBaseRepository;
import com.example.campusrepair.repository.KnowledgeHistoryRepository;
import com.example.campusrepair.service.DiagnosisRules;
import com.example.campusrepair.service.EmbeddingService;
import com.example.campusrepair.service.KnowledgeService;
import com.example.campusrepair.util.CosineSimilarity;
import com.example.campusrepair.util.VectorBytes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 知识库业务实现（阶段一）
 *
 * @Description 新增/编辑（写历史快照）/审核四态流转/回滚/软删/浏览/检索/
 *              helpful 原子累加。检索走 LIKE 关键词匹配（阶段二换向量化时
 *              仅替换本类实现，接口签名不变）。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 审核状态机合法取值 */
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "OFFLINE");

    /** RAG 检索最多返回的命中条目数 */
    private static final int TOP_K = 3;

    /**
     * LIKE 降级时的口语同义词：学生不会用"忽冷忽热"这种标准词。
     * 向量服务可用时不会走这里；只在向量不可用时保证常见口语仍能命中种子条目。
     */
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

    /** 向量检索最低相似度阈值（可经 rag.min-similarity 配置） */
    @org.springframework.beans.factory.annotation.Value("${rag.min-similarity:0.6}")
    private double minSimilarity;

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeHistoryRepository knowledgeHistoryRepository;
    private final DiagnosisSessionRepository diagnosisSessionRepository;
    private final EmbeddingService embeddingService;

    /* ---------- 管理员端 ---------- */

    @Override
    @Transactional
    public KnowledgeBase create(KnowledgeSaveRequest request) {
        LocalDateTime now = LocalDateTime.now();
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTitle(request.getTitle());
        kb.setKeyword(request.getKeyword().trim());
        kb.setQuestion(request.getQuestion().trim());
        kb.setAnswer(request.getAnswer());
        kb.setCategoryId(request.getCategoryId());
        kb.setSource("MANUAL");
        kb.setHelpful(0);
        kb.setReviewStatus("PENDING");
        kb.setIsDeleted(false);
        kb.setGmtCreate(now);
        kb.setGmtModified(now);
        kb.setEmbedding(buildEmbedding(kb.getKeyword() + " " + kb.getQuestion()));
        return knowledgeBaseRepository.save(kb);
    }

    @Override
    @Transactional
    public KnowledgeBase update(Long id, KnowledgeSaveRequest request) {
        KnowledgeBase kb = requireKnowledge(id);
        // 编辑前快照当前内容进历史，形成版本链（只增不改）
        knowledgeHistoryRepository.save(snapshot(kb));
        kb.setTitle(request.getTitle());
        kb.setKeyword(request.getKeyword().trim());
        kb.setQuestion(request.getQuestion().trim());
        kb.setAnswer(request.getAnswer());
        kb.setCategoryId(request.getCategoryId());
        kb.setGmtModified(LocalDateTime.now());
        kb.setEmbedding(buildEmbedding(kb.getKeyword() + " " + kb.getQuestion()));
        return knowledgeBaseRepository.save(kb);
    }

    @Override
    @Transactional
    public KnowledgeBase review(Long id, String reviewStatus) {
        String target = reviewStatus == null ? "" : reviewStatus.trim().toUpperCase();
        if (!REVIEW_STATUSES.contains(target)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "审核状态不合法：" + reviewStatus + "（允许值 PENDING/APPROVED/REJECTED/OFFLINE）");
        }
        KnowledgeBase kb = requireKnowledge(id);
        kb.setReviewStatus(target);
        kb.setGmtModified(LocalDateTime.now());
        return knowledgeBaseRepository.save(kb);
    }

    @Override
    @Transactional
    public KnowledgeBase rollback(Long historyId) {
        KnowledgeHistory history = knowledgeHistoryRepository.findById(historyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "历史快照不存在：" + historyId));
        KnowledgeBase kb = requireKnowledge(history.getKnowledgeId());
        // 回滚前的现状先再次快照，保证版本链不断、可"撤销回滚"
        knowledgeHistoryRepository.save(snapshot(kb));

        kb.setTitle(history.getTitle());
        kb.setQuestion(history.getQuestion());
        kb.setAnswer(history.getAnswer());
        kb.setReviewStatus(history.getReviewStatus());
        kb.setGmtModified(LocalDateTime.now());
        KnowledgeBase saved = knowledgeBaseRepository.save(kb);
        log.info("知识条目 {} 已回滚到快照 {}（操作人 {}）", kb.getId(), historyId, AuthContext.userId());
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = requireKnowledge(id);
        kb.setIsDeleted(true);
        kb.setGmtModified(LocalDateTime.now());
        knowledgeBaseRepository.save(kb);
    }

    @Override
    @Slave
    public List<KnowledgeBase> adminList(String reviewStatus, Long categoryId) {
        String status = reviewStatus == null || reviewStatus.isBlank()
                ? null : reviewStatus.trim().toUpperCase();
        return knowledgeBaseRepository.adminList(status, categoryId);
    }

    @Override
    @Slave
    public List<KnowledgeHistory> history(Long knowledgeId) {
        return knowledgeHistoryRepository.findByKnowledgeIdOrderByIdAsc(knowledgeId);
    }

    /* ---------- 学生端 ---------- */

    @Override
    @Slave
    public List<KnowledgeBase> browse(Long categoryId) {
        return knowledgeBaseRepository.browseApproved(categoryId);
    }

    @Override
    @Slave
    public List<KnowledgeBase> search(String keyword) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return knowledgeBaseRepository.searchApproved(q);
    }

    @Override
    @Transactional
    public long helpful(Long id) {
        int rows = knowledgeBaseRepository.incrementHelpful(id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识条目不存在或未上架：" + id);
        }
        // 读回最新计数返回给前端展示（同主库读，避免主从延迟读到旧值）
        return knowledgeBaseRepository.findByIdAndIsDeletedFalse(id)
                .map(kb -> kb.getHelpful().longValue())
                .orElse(0L);
    }

    /* ---------- 自助诊断（RAG 问答） ---------- */

    @Override
    public DiagnosisResult ask(Long userId, String question, String conversationId) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空");
        }

        // 第一步：检索。优先向量语义检索；向量服务不可用/条目未向量化时降级 LIKE
        DiagnosisResult result = new DiagnosisResult();
        List<DiagnosisResult.Match> matches;
        float[] queryVector = embeddingService.embed(q).orElse(null);
        if (queryVector != null) {
            matches = vectorSearch(queryVector);
            result.setSearchMode("VECTOR");
        } else {
            matches = likeSearch(q);
            result.setSearchMode("LIKE");
        }

        if (matches.isEmpty()) {
            result.setAnswer("暂时没有找到与您问题相关的知识条目，建议直接报修，由维修师傅处理。");
        } else {
            DiagnosisResult.Match top = matches.get(0);
            result.setAnswer(top.getAnswer());
            result.setCategoryId(top.getCategoryId());
        }
        result.setMatches(matches);
        result.setConversationId(conversationId);
        // 紧急等级由规则推导，落库和流式回答共用同一个判断，避免前后矛盾
        String emergencyLevel = DiagnosisRules.emergencyLevel(DiagnosisRules.isEmergency(q));
        result.setEmergencyLevel(emergencyLevel);

        // 第二步：问答记录落库（diagnosis_session），形成数据闭环
        DiagnosisSession session = new DiagnosisSession();
        session.setUserId(userId);
        session.setConversationId(conversationId);
        session.setQuestion(q);
        session.setAnswer(result.getAnswer());
        session.setEmergencyLevel(emergencyLevel);
        if (!matches.isEmpty()) {
            DiagnosisResult.Match top = matches.get(0);
            session.setMatchedKbId(top.getId());
            session.setCategoryId(top.getCategoryId());
            if (top.getSimilarity() != null) {
                session.setSimilarity(java.math.BigDecimal.valueOf(top.getSimilarity())
                        .setScale(3, java.math.RoundingMode.HALF_UP));
            }
        }
        // NULL = 未反馈；只有用户点了"解决了/没解决"才写 1/0，解决率分母据此统计
        session.setIsSolved(null);
        session.setGmtCreate(LocalDateTime.now());
        session.setGmtModified(LocalDateTime.now());
        DiagnosisSession saved = diagnosisSessionRepository.save(session);
        result.setSessionId(saved.getId());
        return result;
    }

    @Override
    @Transactional
    public void markSolved(Long sessionId, Long userId, boolean solved) {
        DiagnosisSession session = diagnosisSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "会话不存在：" + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "只能反馈自己的会话");
        }
        session.setIsSolved(solved);
        session.setGmtModified(LocalDateTime.now());
        diagnosisSessionRepository.save(session);
        // 解决了 → 命中条目 helpful +1，问答反哺知识库排序
        if (solved && session.getMatchedKbId() != null) {
            knowledgeBaseRepository.incrementHelpful(session.getMatchedKbId());
        }
    }

    /** 向量检索：对全部已上架条目算余弦相似度，Top-K（几百条内存计算毫秒级） */
    private List<DiagnosisResult.Match> vectorSearch(float[] queryVector) {
        record Scored(KnowledgeBase kb, double similarity) {
        }
        List<Scored> scored = knowledgeBaseRepository.browseApproved(null).stream()
                .map(kb -> {
                    float[] kbVector = VectorBytes.toFloats(kb.getEmbedding());
                    double similarity = kbVector == null ? 0 : CosineSimilarity.of(queryVector, kbVector);
                    return new Scored(kb, similarity);
                })
                .sorted(java.util.Comparator.comparingDouble(Scored::similarity).reversed())
                .toList();
        // 打印 Top-5 相似度，便于调参（阈值 rag.min-similarity）
        log.info("RAG 向量检索 Top-5 相似度: {}", scored.stream().limit(5)
                .map(s -> s.kb().getId() + "=" + String.format("%.4f", s.similarity()))
                .toList());
        return scored.stream()
                .filter(s -> s.similarity() >= minSimilarity)
                .limit(TOP_K)
                .map(s -> toMatch(s.kb(), s.similarity()))
                .toList();
    }

    /** LIKE 降级检索：原文 → 口语同义词 → 种子关键词反查，逐级兜底 */
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
        // 学生常写"宿舍跳闸了怎么办"这种短句，整句 LIKE 匹配不到，
        // 但种子 keyword 里的"跳闸"就包含在问题里，反查一次即可命中。
        return keywordReverseSearch(q);
    }

    /** 用知识条目自己的 keyword 去比对问题文本，命中数多的排前面 */
    private List<DiagnosisResult.Match> keywordReverseSearch(String q) {
        String lower = q.toLowerCase();
        return knowledgeBaseRepository.browseApproved(null).stream()
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
            // 单字词（如"水"）区分度太低，不拿来反查
            if (normalized.length() >= 2 && lowerQuestion.contains(normalized)) {
                hits++;
            }
        }
        return hits;
    }

    private List<DiagnosisResult.Match> searchMatches(String query) {
        return knowledgeBaseRepository.searchApproved(query).stream()
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

    /** 知识入库/更新时的向量化（key 未配置返回 null，列保持空） */
    private byte[] buildEmbedding(String text) {
        return embeddingService.embed(text)
                .map(VectorBytes::toBytes)
                .orElse(null);
    }

    /* ---------- 私有工具 ---------- */

    /** 取未删除的知识条目，不存在/已删则 404 */
    private KnowledgeBase requireKnowledge(Long id) {
        return knowledgeBaseRepository.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "知识条目不存在：" + id));
    }

    /** 把知识条目当前内容拷贝为历史快照，操作人取当前登录人 */
    private KnowledgeHistory snapshot(KnowledgeBase kb) {
        KnowledgeHistory h = new KnowledgeHistory();
        h.setKnowledgeId(kb.getId());
        h.setTitle(kb.getTitle());
        h.setQuestion(kb.getQuestion());
        h.setAnswer(kb.getAnswer());
        h.setReviewStatus(kb.getReviewStatus());
        h.setOperatorId(AuthContext.userId());
        LocalDateTime now = LocalDateTime.now();
        h.setGmtCreate(now);
        h.setGmtModified(now);
        return h;
    }
}
