package com.example.campusrepair.service.impl;

import com.example.campusrepair.repository.DiagnosisSessionRepository;
import com.example.campusrepair.service.KnowledgeStreamService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SSE 降级通道适配层
 *
 * @Description 不再写任何诊断逻辑：创建 SseEventSink 后直接交给 DiagnosisEngine。
 *              热门问题与统计仍由本类提供，供前端首屏展示。
 * @Author 闵娟
 * @Date 2026-11-21
 * @Version 1.1
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeStreamServiceImpl implements KnowledgeStreamService {

    private static final long EMITTER_TIMEOUT = 300_000L;

    private final DiagnosisEngine diagnosisEngine;
    private final DiagnosisSessionRepository diagnosisSessionRepository;

    @Override
    public SseEmitter streamAsk(Long userId, String question, String conversationId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT);
        diagnosisEngine.submit(userId, question, conversationId, new SseEventSink(emitter));
        return emitter;
    }

    @Override
    public List<Map<String, Object>> hotQuestions() {
        List<Object[]> rows = diagnosisSessionRepository.findHotQuestions(PageRequest.of(0, 5));
        if (!rows.isEmpty()) {
            return rows.stream().map(row -> {
                Map<String, Object> item = new HashMap<>();
                item.put("question", String.valueOf(row[0]));
                item.put("count", ((Number) row[1]).longValue());
                return item;
            }).toList();
        }
        // 冷启动兜底：演示环境第一次进来也有东西可点，不留空白页
        return List.of(
                "电热水器为什么不加热？", "路由器亮红灯怎么办？", "宿舍跳闸了怎么办？",
                "水龙头漏水怎么办？", "门锁打不开怎么办？").stream()
                .map(q -> Map.<String, Object>of("question", q, "count", 0L))
                .toList();
    }

    @Override
    public Map<String, Object> stats() {
        long total = diagnosisSessionRepository.count();
        long solved = diagnosisSessionRepository.countByIsSolvedTrue();
        long feedback = diagnosisSessionRepository.countByIsSolvedIsNotNull();
        long aborted = diagnosisSessionRepository.countByAbortedTrue();
        // 中止不进"未命中"：那是另一类（用户主动停止），混进来会让统计失真
        long noHit = diagnosisSessionRepository.countByMatchedKbIdIsNullAndAbortedFalse();
        Map<String, Object> result = new HashMap<>();
        result.put("total", total);
        result.put("solved", solved);
        result.put("feedback", feedback);
        result.put("aborted", aborted);
        result.put("noHit", noHit);
        // 分母用"已反馈数"，未反馈的会话不算没解决，避免解决率被稀释得很难看
        result.put("solvedRate", feedback == 0 ? 0.0
                : Math.round(solved * 1000.0 / feedback) / 10.0);
        return result;
    }
}
