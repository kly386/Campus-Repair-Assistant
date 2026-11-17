package com.example.campusrepair.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 知识条目新增/编辑请求体
 *
 * @Description 管理员端创建与编辑共用。审核状态、来源、帮助数等由服务端
 *              控制，不接受前端传入（防止越权直改审核状态）。
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
@Data
public class KnowledgeSaveRequest {

    /** 标题（可空） */
    @Size(max = 100, message = "标题不能超过 100 字")
    private String title;

    /** 关键词（检索与去重用） */
    @NotBlank(message = "关键词不能为空")
    @Size(max = 100, message = "关键词不能超过 100 字")
    private String keyword;

    /** 问题（故障现象） */
    @NotBlank(message = "问题不能为空")
    @Size(max = 255, message = "问题不能超过 255 字")
    private String question;

    /** 答案（自助解决方案） */
    @NotBlank(message = "答案不能为空")
    private String answer;

    /** 故障类别 ID（可空 = 未归类） */
    private Long categoryId;
}
