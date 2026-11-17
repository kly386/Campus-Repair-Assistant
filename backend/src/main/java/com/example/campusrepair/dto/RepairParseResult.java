package com.example.campusrepair.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能报修解析结果
 *
 * @Description POST /api/orders/parse 的返回体：
 *              本地正则抽取楼栋/房间（高置信），大模型兜底分类/紧急度，
 *              全部字段经后端校验后返回；aiParsedInfo 为落库用的原始 JSON。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
@Data
public class RepairParseResult {

    /** 建议工单标题（≤50字，可直接作为提交的 title） */
    private String title;

    /** 故障类别名（已校验，取自 repair_category） */
    private String categoryName;

    /** 故障类别编码（ WATER_ELECTRIC/NETWORK/FURNITURE/OTHER ） */
    private String categoryCode;

    /** 故障类别 ID（可直接回填提交体 category_id） */
    private Long categoryId;

    /** 楼栋（正则优先，LLM 兜底） */
    private String building;

    /** 房间号（正则优先，LLM 兜底） */
    private String room;

    /** 紧急度 LOW/MEDIUM/HIGH（大写，与库约定一致） */
    private String emergencyLevel;

    /** 综合置信度 0~1 */
    private Double confidence;

    /** 落库 JSON（提交时回填 repair_order.ai_parsed_info） */
    private String aiParsedInfo;

    /** 是否 mock 模式产出（前端可提示"演示数据"） */
    private boolean mock;

    /** 解析过程提示（如"楼栋由本地规则抽取""AI 超时已降级"） */
    private List<String> notes = new ArrayList<>();
}
