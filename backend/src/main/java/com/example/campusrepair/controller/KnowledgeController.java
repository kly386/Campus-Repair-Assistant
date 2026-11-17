package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.dto.KnowledgeSaveRequest;
import com.example.campusrepair.entity.KnowledgeBase;
import com.example.campusrepair.entity.KnowledgeHistory;
import com.example.campusrepair.security.RequirePermission;
import com.example.campusrepair.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 知识库接口
 *
 * @Description 学生口（浏览/检索/有帮助计数，权限 menu:diagnosis）与
 *              管理员口（增删改/审核/回滚/版本历史，权限 knowledge:manage，
 *              权限码见 docs/sql/knowledge-patch.sql）。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /* ---------- 学生口 ---------- */

    /** 全部已上架知识（有帮助数排序） */
    @GetMapping
    @RequirePermission("menu:diagnosis")
    public ApiResponse<List<KnowledgeBase>> browseAll() {
        return ApiResponse.success(knowledgeService.browse(null));
    }

    /** 按故障类别浏览已上架知识 */
    @GetMapping("/category/{categoryId}")
    @RequirePermission("menu:diagnosis")
    public ApiResponse<List<KnowledgeBase>> browseByCategory(@PathVariable Long categoryId) {
        return ApiResponse.success(knowledgeService.browse(categoryId));
    }

    /** 关键词检索（阶段一 LIKE 保底；阶段二升级为向量检索，路径不变） */
    @GetMapping("/search")
    @RequirePermission("menu:diagnosis")
    public ApiResponse<List<KnowledgeBase>> search(@RequestParam String q) {
        return ApiResponse.success(knowledgeService.search(q));
    }

    /** "有帮助"计数 +1，返回最新计数 */
    @PostMapping("/helpful/{id}")
    @RequirePermission("menu:diagnosis")
    public ApiResponse<Map<String, Long>> helpful(@PathVariable Long id) {
        long count = knowledgeService.helpful(id);
        return ApiResponse.success("感谢反馈", Map.of("helpful", count));
    }

    /** 自助诊断提问：RAG 检索 Top-K + 问答记录落库 */
    @PostMapping("/session")
    @RequirePermission("menu:diagnosis")
    public ApiResponse<DiagnosisResult> ask(@RequestBody Map<String, String> body) {
        String question = body == null ? null : body.get("question");
        return ApiResponse.success(knowledgeService.ask(AuthContext.userId(), question));
    }

    /** 问答反馈：?solved=true|false（解决时命中条目 helpful +1） */
    @PutMapping("/session/{sessionId}/solved")
    @RequirePermission("menu:diagnosis")
    public ApiResponse<Void> markSolved(@PathVariable Long sessionId,
                                        @RequestParam(defaultValue = "true") boolean solved) {
        knowledgeService.markSolved(sessionId, AuthContext.userId(), solved);
        return ApiResponse.success("感谢反馈", null);
    }

    /* ---------- 管理员口 ---------- */

    /** 新增（初始 PENDING 待审核） */
    @PostMapping
    @RequirePermission("knowledge:manage")
    public ApiResponse<KnowledgeBase> create(@Valid @RequestBody KnowledgeSaveRequest request) {
        return ApiResponse.success(knowledgeService.create(request));
    }

    /** 编辑（旧内容自动进历史快照） */
    @PutMapping("/{id}")
    @RequirePermission("knowledge:manage")
    public ApiResponse<KnowledgeBase> update(@PathVariable Long id,
                                             @Valid @RequestBody KnowledgeSaveRequest request) {
        return ApiResponse.success(knowledgeService.update(id, request));
    }

    /** 审核状态流转：?status=PENDING|APPROVED|REJECTED|OFFLINE */
    @PutMapping("/review/{id}")
    @RequirePermission("knowledge:manage")
    public ApiResponse<KnowledgeBase> review(@PathVariable Long id, @RequestParam String status) {
        return ApiResponse.success(knowledgeService.review(id, status));
    }

    /** 回滚到指定历史快照（回滚前现状先再次快照） */
    @PostMapping("/rollback/{historyId}")
    @RequirePermission("knowledge:manage")
    public ApiResponse<KnowledgeBase> rollback(@PathVariable Long historyId) {
        return ApiResponse.success(knowledgeService.rollback(historyId));
    }

    /** 软删除（is_deleted=1，历史保留） */
    @DeleteMapping("/{id}")
    @RequirePermission("knowledge:manage")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return ApiResponse.success("已删除", null);
    }

    /** 管理员列表：?status=&categoryId= 全部可选 */
    @GetMapping("/admin/list")
    @RequirePermission("knowledge:manage")
    public ApiResponse<List<KnowledgeBase>> adminList(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long categoryId) {
        return ApiResponse.success(knowledgeService.adminList(status, categoryId));
    }

    /** 版本历史（最早快照在前） */
    @GetMapping("/{id}/history")
    @RequirePermission("knowledge:manage")
    public ApiResponse<List<KnowledgeHistory>> history(@PathVariable Long id) {
        return ApiResponse.success(knowledgeService.history(id));
    }

    /* ---------- 别名路由（对接 KnowledgeReview.vue 前端契约，伍梦瑶） ---------- */

    /** GET /knowledge/list —— 管理员列表别名（同 admin/list） */
    @GetMapping("/list")
    @RequirePermission("knowledge:manage")
    public ApiResponse<List<KnowledgeBase>> listAlias(@RequestParam(required = false) String status,
                                                      @RequestParam(required = false) Long categoryId) {
        return adminList(status, categoryId);
    }

    /** GET /knowledge/history/{knowledgeId} —— 版本历史别名（同 /{id}/history） */
    @GetMapping("/history/{knowledgeId}")
    @RequirePermission("knowledge:manage")
    public ApiResponse<List<KnowledgeHistory>> historyAlias(@PathVariable Long knowledgeId) {
        return history(knowledgeId);
    }
}
