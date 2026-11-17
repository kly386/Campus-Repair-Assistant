package com.example.campusrepair.service;

import com.example.campusrepair.dto.RepairParseResult;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 智能报修解析服务
 *
 * @Description 文字 + 拍照 → 结构化解析（类别/楼栋/房间/紧急度/标题/置信度）。
 *              策略遵循 docs/技术难点分析.md §1：
 *              楼栋/房间正则优先抽取，LLM 只兜底；类别非法回落 OTHER；
 *              LLM 失败/超时整体降级为纯本地规则结果，绝不阻塞提交。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface AiRepairParseService {

    /**
     * 解析故障描述与照片
     *
     * @param text   用户文字描述
     * @param images 用户拍照（可空）
     * @return 结构化解析结果（永不抛异常，失败自动降级）
     */
    RepairParseResult parse(String text, List<MultipartFile> images);
}
