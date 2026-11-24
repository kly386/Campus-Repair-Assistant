package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.dto.KnowledgeSaveRequest;
import com.example.campusrepair.entity.DiagnosisSession;
import com.example.campusrepair.entity.KnowledgeHistory;
import com.example.campusrepair.entity.KnowledgeBase;
import com.example.campusrepair.repository.DiagnosisSessionRepository;
import com.example.campusrepair.repository.KnowledgeHistoryRepository;
import com.example.campusrepair.repository.KnowledgeBaseRepository;
import com.example.campusrepair.service.DiagnosisRules;
import com.example.campusrepair.service.KnowledgeService;
import com.example.campusrepair.service.KnowledgeVectorCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 知识库业务实现（knowledge_base 唯一数据源）
 *
 * @Description CRUD（写历史快照）/审核四态流转/回滚/软删/浏览/搜索/helpful，
 *              以及诊断问答落库。检索统一委托 KnowledgeVectorCache：
 *              启动全量向量进内存、余弦 Top3、异常降级 MySQL keywords LIKE。
 * @Author 闵娟
 * @Date 2026-11-17
 * @Version 1.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 审核状态机合法取值 */
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "OFFLINE");

    private final KnowledgeBaseRepository knowledgeRepository;
    private final KnowledgeHistoryRepository knowledgeHistoryRepository;
    private final DiagnosisSessionRepository diagnosisSessionRepository;
    private final KnowledgeVectorCache knowledgeVectorCache;

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
        return knowledgeRepository.save(kb);
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
        return knowledgeRepository.save(kb);
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
        return knowledgeRepository.save(kb);
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
        KnowledgeBase saved = knowledgeRepository.save(kb);
        log.info("知识条目 {} 已回滚到快照 {}（操作人 {}）", kb.getId(), historyId, AuthContext.userId());
        return saved;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        KnowledgeBase kb = requireKnowledge(id);
        kb.setIsDeleted(true);
        kb.setGmtModified(LocalDateTime.now());
        knowledgeRepository.save(kb);
    }

    @Override
    @Slave
    public List<KnowledgeBase> adminList(String reviewStatus, Long categoryId) {
        String status = reviewStatus == null || reviewStatus.isBlank()
                ? null : reviewStatus.trim().toUpperCase();
        return knowledgeRepository.adminList(status, categoryId);
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
        return knowledgeRepository.browseApproved(categoryId);
    }

    @Override
    @Slave
    public List<KnowledgeBase> search(String keyword) {
        String q = keyword == null ? "" : keyword.trim();
        if (q.isEmpty()) {
            return List.of();
        }
        return knowledgeRepository.searchApproved(q);
    }

    @Override
    @Transactional
    public long helpful(Long id) {
        int rows = knowledgeRepository.incrementHelpful(id);
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "知识条目不存在或未上架：" + id);
        }
        return knowledgeRepository.findByIdAndIsDeletedFalse(id)
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

        // 检索统一走内存向量缓存（异常内部降级 LIKE）
        DiagnosisResult result = new DiagnosisResult();
        List<DiagnosisResult.Match> matches = knowledgeVectorCache.search(q);
        result.setSearchMode(matches.stream().anyMatch(m -> m.getSimilarity() != null) ? "VECTOR" : "LIKE");
        if (matches.isEmpty()) {
            result.setAnswer("暂时没有找到与您问题相关的知识条目，建议直接报修，由维修师傅处理。");
        } else {
            DiagnosisResult.Match top = matches.get(0);
            result.setAnswer(top.getAnswer());
            result.setCategoryId(top.getCategoryId());
        }
        result.setMatches(matches);
        result.setConversationId(conversationId);
        // 紧急等级由规则推导，落库和流式回答共用同一个判断
        String emergencyLevel = DiagnosisRules.emergencyLevel(DiagnosisRules.isEmergency(q));
        result.setEmergencyLevel(emergencyLevel);

        // 问答记录落库（diagnosis_session），aborted 默认 false
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
        session.setIsSolved(null);
        session.setAborted(false);
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
        if (solved && session.getMatchedKbId() != null) {
            knowledgeRepository.incrementHelpful(session.getMatchedKbId());
        }
    }

    /* ---------- 私有工具 ---------- */

    private KnowledgeBase requireKnowledge(Long id) {
        return knowledgeRepository.findByIdAndIsDeletedFalse(id)
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
