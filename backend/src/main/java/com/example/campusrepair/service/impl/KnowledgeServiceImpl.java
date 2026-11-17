package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.datasource.Slave;
import com.example.campusrepair.dto.KnowledgeSaveRequest;
import com.example.campusrepair.entity.KnowledgeBase;
import com.example.campusrepair.entity.KnowledgeHistory;
import com.example.campusrepair.repository.KnowledgeBaseRepository;
import com.example.campusrepair.repository.KnowledgeHistoryRepository;
import com.example.campusrepair.service.KnowledgeService;
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
 * 知识库业务实现（阶段一）
 *
 * @Description 新增/编辑（写历史快照）/审核四态流转/回滚/软删/浏览/检索/
 *              helpful 原子累加。检索走 LIKE 关键词匹配（阶段二换向量化时
 *              仅替换本类实现，接口签名不变）。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeServiceImpl implements KnowledgeService {

    /** 审核状态机合法取值 */
    private static final Set<String> REVIEW_STATUSES = Set.of("PENDING", "APPROVED", "REJECTED", "OFFLINE");

    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final KnowledgeHistoryRepository knowledgeHistoryRepository;

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
