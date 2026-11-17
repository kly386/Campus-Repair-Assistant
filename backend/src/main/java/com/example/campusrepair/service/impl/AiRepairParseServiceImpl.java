package com.example.campusrepair.service.impl;

import com.example.campusrepair.config.AiProperties;
import com.example.campusrepair.dto.RepairParseResult;
import com.example.campusrepair.service.AiClient;
import com.example.campusrepair.service.AiRepairParseService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能报修解析服务实现
 *
 * @Description 解析流水：①正则抽楼栋/房间 ②查类别表 ③组提示词调 AiClient（带图）
 *              ④解析 LLM JSON 并校验类别/紧急度 ⑤合并结果（正则字段优先）。
 *              任何一步失败都不抛异常：LLM 失败→纯本地规则降级，保证可提交。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRepairParseServiceImpl implements AiRepairParseService {

    /** 楼栋正则：如 3号宿舍楼 / 紫荆2号楼 / 桃李园A栋 / 5栋 */
    private static final Pattern BUILDING_PATTERN = Pattern.compile(
            "([\\u4e00-\\u9fa5A-Za-z]{0,6}[0-9一二三四五六七八九十]{1,3}\\s*(?:号)?(?:宿舍楼|公寓楼|公寓|号楼|栋|楼))");

    /** 房间号正则：如 302 / B512 / 302室（用断言而非 \b，避免中文"楼/号"字符干扰边界） */
    private static final Pattern ROOM_PATTERN = Pattern.compile(
            "(?<![0-9])([A-Za-z]?\\d{3,5}(?:室|房)?)(?![0-9])");

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public RepairParseResult parse(String text, List<MultipartFile> images) {
        String safeText = text == null ? "" : text.trim();
        List<String> notes = new ArrayList<>();
        if (!StringUtils.hasText(safeText) && (images == null || images.isEmpty())) {
            throw new IllegalArgumentException("请先填写故障描述或上传照片");
        }

        // ① 正则优先抽取楼栋/房间（本地、零成本、高置信）
        String buildingByRegex = extractBuilding(safeText);
        String roomByRegex = extractRoom(safeText);
        if (buildingByRegex != null) {
            notes.add("楼栋由本地规则抽取，置信度高");
        }
        if (roomByRegex != null) {
            notes.add("房间号由本地规则抽取，置信度高");
        }

        // ② 类别表（id/name/code）供校验与回填
        List<Map<String, Object>> categories = jdbcTemplate.queryForList(
                "SELECT id, name, code FROM repair_category WHERE is_deleted = 0");

        // ③④ 组提示词调模型（带图），失败自动降级
        String llmJson = null;
        boolean mockUsed = false;
        boolean llmFailed = false;
        try {
            String userPrompt = buildUserPrompt(safeText, categories);
            AiClient.ChatResponse resp = aiClient.chat(new AiClient.ChatRequest(
                    systemPrompt(), userPrompt, toDataUris(images), "repair_parse"));
            llmJson = resp.content();
            mockUsed = resp.mock();
        } catch (Exception e) {
            llmFailed = true;
            notes.add("AI 解析失败，已按本地规则降级解析");
        }

        // ⑤ 解析 LLM 输出并校验
        JsonNode llm = parseLlmJson(llmJson);
        String title = pickTitle(safeText, llm);
        String emergency = normalizeEmergency(llm == null ? null : llm.path("emergency_level").asText(null));
        Double llmConfidence = llm == null ? null : llm.path("confidence").asDouble(0.0);

        String building = buildingByRegex != null ? buildingByRegex
                : (llm != null && StringUtils.hasText(llm.path("building").asText(null))
                        ? llm.path("building").asText().trim() : null);
        String room = roomByRegex != null ? roomByRegex
                : (llm != null && StringUtils.hasText(llm.path("room").asText(null))
                        ? llm.path("room").asText().trim() : null);

        // 类别校验：匹配不上回落 OTHER
        String llmCategory = llm == null ? null : llm.path("category").asText(null);
        Map<String, Object> matched = matchCategory(llmCategory, categories);
        Long categoryId = matched == null ? fallbackOtherId(categories) : asLong(matched.get("id"));
        String categoryName = matched == null ? "报修咨询" : (String) matched.get("name");
        String categoryCode = matched == null ? "OTHER" : (String) matched.get("code");
        if (matched == null) {
            notes.add("AI 类别不可识别，已回落 OTHER");
        }

        // 置信度：正则命中抬升，纯 LLM 用其自评，降级给低值
        double confidence;
        if (llmFailed) {
            confidence = 0.30;
        } else if (buildingByRegex != null && roomByRegex != null) {
            confidence = Math.max(llmConfidence == null ? 0 : llmConfidence, 0.85);
        } else {
            confidence = llmConfidence == null ? 0.50 : Math.max(0.05, Math.min(llmConfidence, 0.99));
        }

        RepairParseResult result = new RepairParseResult();
        result.setTitle(title);
        result.setCategoryName(categoryName);
        result.setCategoryCode(categoryCode);
        result.setCategoryId(categoryId);
        result.setBuilding(building);
        result.setRoom(room);
        result.setEmergencyLevel(emergency);
        result.setConfidence(round(confidence));
        result.setMock(mockUsed);
        result.setNotes(notes);

        // aiParsedInfo：落库 JSON（ai_confidence 与之同源）
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("title", title);
        info.put("category", categoryName);
        info.put("categoryCode", categoryCode);
        info.put("emergencyLevel", emergency);
        info.put("building", building);
        info.put("room", room);
        info.put("confidence", round(confidence));
        info.put("source", llmFailed ? "regex-fallback" : (mockUsed ? "regex+llm-mock" : "regex+llm"));
        info.put("notes", notes);
        try {
            result.setAiParsedInfo(objectMapper.writeValueAsString(info));
        } catch (Exception e) {
            result.setAiParsedInfo("{}");
        }
        return result;
    }

    /** 系统提示词：强制 JSON 输出、约束只填 JSON 字段（报修解析专用模板，RAG 另行扩展） */
    private String systemPrompt() {
        return "你是校园宿舍报修分诊助手。根据用户描述和照片判断故障类别与紧急度，"
                + "严格只输出一个 JSON 对象，不要输出任何多余文字或 Markdown 代码块。"
                + "JSON 字段固定为：title(不超过20字的故障标题)、category(故障类别名，必须从候选列表中选)、"
                + "emergency_level(只能是 LOW/MEDIUM/HIGH)、building(楼栋，文本中未出现则填 null)、"
                + "room(房间号，文本中未出现则填 null)、confidence(0~1 的小数)。"
                + "注意：漏水/断电等影响生活的填 HIGH；普通故障填 MEDIUM；轻微问题填 LOW。";
    }

    /** 用户提示词：描述 + 候选类别清单 */
    private String buildUserPrompt(String text, List<Map<String, Object>> categories) {
        StringBuilder sb = new StringBuilder("故障描述：").append(text).append("\n候选类别：");
        for (Map<String, Object> c : categories) {
            sb.append(c.get("name")).append("(code=").append(c.get("code")).append(") ");
        }
        return sb.toString();
    }

    /** 图片转 data URI（base64），并按配置截断数量 */
    private List<String> toDataUris(List<MultipartFile> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        List<String> uris = new ArrayList<>();
        for (MultipartFile img : images) {
            if (uris.size() >= aiProperties.getMaxImages()) {
                break;
            }
            try {
                String type = StringUtils.hasText(img.getContentType())
                        ? img.getContentType() : "image/png";
                uris.add("data:" + type + ";base64,"
                        + Base64.getEncoder().encodeToString(img.getBytes()));
            } catch (Exception e) {
                log.warn("图片读取失败，跳过: {}", e.getMessage());
            }
        }
        return uris.isEmpty() ? null : uris;
    }

    /** 剥掉可能的 ```json 围栏后解析，失败返回 null（不抛） */
    private JsonNode parseLlmJson(String llmJson) {
        if (!StringUtils.hasText(llmJson)) {
            return null;
        }
        String cleaned = llmJson.trim();
        cleaned = cleaned.replaceFirst("^```(?:json)?", "").replaceFirst("```$", "").trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) {
            cleaned = cleaned.substring(start, end + 1);
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("LLM 输出非 JSON，忽略: {}", StringUtils.hasLength(llmJson) ? llmJson.substring(0, Math.min(80, llmJson.length())) : "");
            return null;
        }
    }

    private String extractBuilding(String text) {
        Matcher m = BUILDING_PATTERN.matcher(text);
        return m.find() ? m.group(1).replaceAll("\\s+", "") : null;
    }

    private String extractRoom(String text) {
        Matcher m = ROOM_PATTERN.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /** 按名称（含）或编码精确匹配类别 */
    private Map<String, Object> matchCategory(String llmCategory, List<Map<String, Object>> categories) {
        if (!StringUtils.hasText(llmCategory)) {
            return null;
        }
        String target = llmCategory.trim();
        for (Map<String, Object> c : categories) {
            String name = String.valueOf(c.get("name"));
            if (name.contains(target) || target.contains(name)) {
                return c;
            }
        }
        for (Map<String, Object> c : categories) {
            if (target.equalsIgnoreCase(String.valueOf(c.get("code")))) {
                return c;
            }
        }
        return null;
    }

    /** 兜底类别：取 code=OTHER 的第一条；再兜底取第一条 */
    private Long fallbackOtherId(List<Map<String, Object>> categories) {
        for (Map<String, Object> c : categories) {
            if ("OTHER".equalsIgnoreCase(String.valueOf(c.get("code")))) {
                return asLong(c.get("id"));
            }
        }
        return categories.isEmpty() ? null : asLong(categories.get(0).get("id"));
    }

    /** BIGINT UNSIGNED 经 JDBC 可能是 BigInteger/Long，统一转 Long */
    private Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }

    private String pickTitle(String text, JsonNode llm) {
        if (llm != null && StringUtils.hasText(llm.path("title").asText(null))) {
            return truncate(llm.path("title").asText().trim(), 50);
        }
        return truncate(text, 50).isEmpty() ? "报修" : truncate(text, 50);
    }

    /** 紧急度规范化：只允许 LOW/MEDIUM/HIGH，缺省 MEDIUM */
    private String normalizeEmergency(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "MEDIUM";
        }
        String upper = raw.trim().toUpperCase();
        return switch (upper) {
            case "LOW", "MEDIUM", "HIGH" -> upper;
            default -> "MEDIUM";
        };
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    private double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
