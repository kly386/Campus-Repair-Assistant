package com.example.campusrepair.service.impl;

import com.example.campusrepair.dto.DiagnosisResult;
import com.example.campusrepair.entity.DiagnosisSession;
import com.example.campusrepair.repository.DiagnosisSessionRepository;
import com.example.campusrepair.service.AbortException;
import com.example.campusrepair.service.AbortHandle;
import com.example.campusrepair.service.AbortRegistry;
import com.example.campusrepair.service.DiagnosisEventSink;
import com.example.campusrepair.service.DiagnosisRules;
import com.example.campusrepair.service.KnowledgeService;
import com.example.campusrepair.service.ZhipuChatStreamService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 自助诊断引擎（唯一业务逻辑）
 *
 * @Description 不依赖 SSE 也不依赖 WebSocket，只通过 DiagnosisEventSink 推事件。
 *              一次问答的完整流程都在这里：检索 → 分层拦截 → grounded 流式生成 →
 *              回填答案 → done（含报修接力字段）。SSE 与 WS 只是两种传输外壳。
 * @Author 闵娟
 * @Date 2026-11-23
 * @Version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisEngine {

    /** 低于此相似度先声明"不太确定"，进一步压制幻觉（与需求分析.md 的 0.7 对齐） */
    @Value("${rag.low-confidence:0.7}")
    private double lowConfidenceThreshold;

    private static final String EMERGENCY_TIP =
            "这个问题涉及用电或用水安全，请不要自行处理！建议立即提交报修工单，"
                    + "由专业维修师傅上门检查，同时联系宿管说明情况。";

    private static final String OFF_TOPIC_TIP =
            "抱歉，我是校园维修小助手，只负责解答宿舍设施维修问题，"
                    + "情感、闲聊或其他问题我帮不上忙。如果您有宿舍维修方面的困扰，请描述具体问题，"
                    + "我来判断能否自行解决。";

    private static final String LOW_CONFIDENCE_TIP =
            "我不太确定下面的方法是否完全适合您的情况，您先参考看看；"
                    + "如果试了不行，建议直接报修，别勉强自己处理。\n\n";

    private static final String NEEDS_CONTEXT_TIP =
            "这句像是接着上一轮在问，但我这边没有保留对应的上下文，不能乱猜。"
                    + "请再说明一下具体是什么设备、什么现象；如果基础排查已经试过，建议直接报修。";

    /** 上一轮已经指向报修后的追问：不再编新步骤，也不再当闲聊拒答 */
    private static final String FOLLOW_UP_AFTER_REPAIR_TIP =
            "我这边没有其他可靠的方法了。再往下自己试容易越弄越糟，"
                    + "建议直接提交报修工单，由师傅上门检查；着急的话同时联系宿管说明情况。";

    /** 同对话收尾：短回一句就停，不再走过检索/闲聊拒答/工单引导 */
    private static final String CLOSING_TIP = "好的，有问题随时再来。";

    /**
     * 五段式固定输出。分步操作只列动作，不生成"成功判据"行；
     * 种子里的判据仅在降级为知识库原文时随原文展示，不进实时生成。
     */
    private static final String SYSTEM_PROMPT = """
            你是"校园维修小助手"，只服务本校学生的宿舍设施维修自助咨询。

            【硬性规则，必须逐条遵守】
            1. 只回答宿舍维修相关问题（水电、热水器、灯具、门锁、路由器、马桶、水龙头、空调等）。
            2. 只能依据下面【知识条目】的内容组织语言。禁止使用你的通用知识，
               禁止编造任何步骤、电话、地点、时间承诺或知识条目里没有的细节。
            3. 情感倾诉、闲聊、学习考试、天气新闻、与维修无关的问题：一律礼貌拒绝，
               并引导用户描述宿舍维修问题或提交报修工单。不要安慰、不要聊天、不要展开话题。
            4. 若知识条目不足以回答，直接说"这个问题我暂时不确定，建议提交报修工单，
               由维修师傅上门处理"，不要猜测、不要补全。
            5. 必须严格按下面五段固定结构输出，每段都要有，段首用①②③④⑤编号；
               禁止使用 Markdown 标题、表格、代码块：
               ①初步判断：一两句话说明最可能的原因。
               ②先确认什么：列出需要用户先确认的 1~3 个现象。
               ③分步操作：按知识条目原文顺序列步骤；每一步单独一行，只写动作。
                 不要写成功判据、"怎么算有效"、"以现场现象为准"等说明，
                 不要在步骤后追加 ↳ 缩进行，也不要把判据挤在动作同一行。
               ④还不行怎么办：明确说明下一步是直接报修还是联系宿管，不要含糊其辞。
               ⑤安全提醒：涉及水电火气必须提示断电断水、不要自行拆修；
                 没有明显风险也要写一句简短提醒。
            6. 结合【最近对话】理解"它""然后呢""试过了"等指代。
               若【反馈】显示"上一轮方法已试过且无效"，不要重复同样的步骤，
               直接进入④并明确建议报修。
            7. 用中文回答，口语化，全文控制在 350 字以内。不要提及"知识条目""检索"
               "相似度""prompt"等内部字眼，就当自己是真人客服在说话。
            """;

    private final KnowledgeService knowledgeService;
    private final ZhipuChatStreamService chatStreamService;
    private final DiagnosisSessionRepository diagnosisSessionRepository;
    private final AbortRegistry abortRegistry;

    /** 流式生成在独立线程池执行，避免长连接占住 Tomcat / WS 线程 */
    private final ExecutorService executor = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "diagnosis-engine-worker");
        t.setDaemon(true);
        return t;
    });

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    /** 提交一次诊断到后台线程执行；SSE 服务和 WS Handler 都只调这一个入口 */
    public void submit(Long userId, String question, String conversationId, DiagnosisEventSink sink) {
        // 注册中止句柄：点"停止"或连接断开时由 AbortRegistry 置位，流式回调据此立即断流
        AbortHandle handle = abortRegistry.register(userId, conversationId);
        executor.submit(() -> {
            try {
                run(userId, question, conversationId, sink, handle);
            } finally {
                abortRegistry.remove(userId, conversationId);
            }
        });
    }

    /** 实际流式流程：上下文 → 检索 → 规则拦截/流式生成 → 回填 → done（中止发 aborted） */
    public void run(Long userId, String question, String conversationId,
                    DiagnosisEventSink sink, AbortHandle abortHandle) {
        String q = question == null ? "" : question.trim();
        if (q.isEmpty()) {
            sink.emit("error", Map.of("message", "问题不能为空"));
            sink.complete();
            return;
        }
        Long sessionId = null;

        try {
            // 1) 先取历史上下文。必须在 ask() 落库之前查，否则会把本轮问题也算进"最近对话"
            List<DiagnosisSession> history = loadHistory(userId, conversationId);

            // 2) 检索 + 落库（紧急等级、类别、conversationId 一并落库，供报修接力使用）
            DiagnosisResult result = knowledgeService.ask(userId, q, conversationId);
            sessionId = result.getSessionId();
            List<DiagnosisResult.Match> matches =
                    result.getMatches() == null ? List.of() : result.getMatches();
            String fallback = result.getAnswer() == null ? "" : result.getAnswer();

            boolean emergency = DiagnosisRules.isEmergency(q);
            boolean hasHistory = !history.isEmpty();
            boolean followUpPhrase = DiagnosisRules.isFollowUp(q);
            // "好吧/好的/谢谢"是收尾，不是闲聊：同对话里才成立，别再甩拒答模板。
            boolean closing = !emergency && hasHistory && DiagnosisRules.isClosing(q);
            // 用户往往不点"没解决"按钮，而是直接打字说"试过了还是不行"。
            // 两种反馈都要认；取最近一轮（history 最后一个元素）而不是最旧一轮。
            // 收尾语优先，避免上一轮反馈过"没解决"时又把"好的"当新反馈。
            boolean triedBefore = !closing && hasHistory
                    && (Boolean.FALSE.equals(latestTurn(history).getIsSolved())
                        || DiagnosisRules.isTriedAndFailed(q));
            // 同一对话里确实有上一轮时，"还有其他方法吗"是追问，不是闲聊，
            // 也不能再说"没有上下文"。真没历史才走 NEEDS_CONTEXT_TIP。
            boolean needsContext = !emergency && !closing && !triedBefore && !hasHistory
                    && matches.isEmpty() && followUpPhrase;
            boolean contextFollowUp = !emergency && !closing && !triedBefore
                    && hasHistory && followUpPhrase;
            boolean repairFollowUp = contextFollowUp && previousSuggestedRepair(history);
            boolean offTopic = !emergency && !closing && !needsContext && !contextFollowUp
                    && !triedBefore && !DiagnosisRules.isRepairRelated(q) && matches.isEmpty();
            Double topSimilarity = matches.isEmpty() ? null : matches.get(0).getSimilarity();
            // 只有真正走"检索 + 模型生成"的分支才示弱；安全拦截/收尾/试过无效/追问
            // 这些都是固定文案，跟相似度无关，不能弹"把握不大"提示。
            boolean lowConfidence = !emergency && !closing && !offTopic && !needsContext
                    && !contextFollowUp && !triedBefore && !repairFollowUp
                    && topSimilarity != null && topSimilarity < lowConfidenceThreshold;

            // 3) meta：先把检索状态和可溯源条目推给前端，撑住过渡态
            Map<String, Object> meta = new HashMap<>();
            meta.put("sessionId", result.getSessionId());
            meta.put("searchMode", result.getSearchMode());
            meta.put("emergency", emergency);
            meta.put("closed", closing);
            meta.put("offTopic", offTopic);
            meta.put("needsContext", needsContext);
            meta.put("triedBefore", triedBefore);
            meta.put("contextFollowUp", contextFollowUp);
            meta.put("lowConfidence", lowConfidence);
            meta.put("matches", (closing || offTopic || emergency || needsContext || triedBefore || repairFollowUp)
                    ? List.of() : matches.stream().map(this::briefMatch).toList());
            sink.emit("meta", meta);

            // 4) 分层决定回答方式
            StringBuilder fullAnswer = new StringBuilder();
            boolean degraded = false;

            if (emergency) {
                // 安全红线：涉及水电火气，直接拦下，绝不让模型给步骤
                appendDelta(sink, fullAnswer, EMERGENCY_TIP);
            } else if (closing) {
                // 收尾语：同对话直接确认，不检索、不调模型、不再引导报修
                appendDelta(sink, fullAnswer, CLOSING_TIP);
            } else if (triedBefore) {
                // 上一轮方法已试过无效：承认事实、停止重复、直接指向报修，不调模型
                appendDelta(sink, fullAnswer, triedBeforeTip(latestTurn(history)));
            } else if (needsContext) {
                appendDelta(sink, fullAnswer, NEEDS_CONTEXT_TIP);
            } else if (offTopic) {
                appendDelta(sink, fullAnswer, OFF_TOPIC_TIP);
            } else if (repairFollowUp) {
                // 上一轮已经指向报修：不再编新步骤，也不当闲聊拒答
                appendDelta(sink, fullAnswer, FOLLOW_UP_AFTER_REPAIR_TIP);
            } else if (matches.isEmpty()) {
                // 无命中兜底，不调 LLM，零幻觉也不花钱
                appendDelta(sink, fullAnswer, fallback);
            } else {
                try {
                    if (lowConfidence) {
                        appendDelta(sink, fullAnswer, LOW_CONFIDENCE_TIP);
                    }
                    StringBuilder generated = new StringBuilder();
                    chatStreamService.streamChat(
                            SYSTEM_PROMPT,
                            buildUserPrompt(q, matches, history, triedBefore),
                            token -> {
                                // 每收到一块增量 token 先查中断标记：中止则抛异常终止上游 SSE 读取
                                if (abortHandle.isAborted()) {
                                    throw new AbortException();
                                }
                                generated.append(token);
                                sink.emit("delta", Map.of("text", token));
                            },
                            abortHandle::isAborted);
                    if (generated.toString().isBlank()) {
                        throw new IllegalStateException("模型返回空回答");
                    }
                    fullAnswer.append(generated);
                } catch (AbortException e) {
                    throw e; // 冒泡到外层，统一发 aborted、不回填、不计未命中
                } catch (Exception e) {
                    // 已经吐出的 token 收不回，补一句说明后接知识库原文
                    log.warn("智谱流式生成失败，降级为知识库原文: {}", e.getMessage());
                    degraded = true;
                    appendDelta(sink, fullAnswer,
                            "\n\n（实时生成暂不可用，以下为知识库原文）\n" + fallback);
                }
            }

            // 5) 回填最终答案，保证数据里存的是学生真正看到的内容
            try {
                diagnosisSessionRepository.findById(result.getSessionId()).ifPresent(session -> {
                    session.setAnswer(fullAnswer.toString());
                    diagnosisSessionRepository.save(session);
                });
            } catch (Exception e) {
                log.warn("回填诊断会话答案失败: {}", e.getMessage());
            }

            // 6) done：除了反馈和追问，还要把报修接力需要的字段一次性交出去
            Map<String, Object> done = new HashMap<>();
            done.put("sessionId", result.getSessionId());
            done.put("diagnosisSessionId", result.getSessionId());
            done.put("conversationId", conversationId);
            done.put("answer", fullAnswer.toString());
            done.put("degraded", degraded);
            done.put("closed", closing);
            done.put("suggestions", buildSuggestions(matches,
                    closing || emergency || offTopic || needsContext || triedBefore || contextFollowUp));
            done.put("categoryId", result.getCategoryId());
            done.put("emergencyLevel", result.getEmergencyLevel());
            done.put("matchedKbId", closing || matches.isEmpty() ? null : matches.get(0).getId());
            done.put("attemptedSteps", attemptedSteps(history, triedBefore || repairFollowUp));
            sink.emit("done", done);
            sink.complete();
        } catch (AbortException e) {
            // 用户主动中止：只打标记并清空答案，不回填半截内容、不发 done。
            // 统计里单独成一类，不混进"知识库未命中"或"报错"。
            if (sessionId != null) {
                try {
                    diagnosisSessionRepository.findById(sessionId).ifPresent(session -> {
                        session.setAborted(true);
                        session.setAnswer(null);
                        diagnosisSessionRepository.save(session);
                    });
                } catch (Exception ex) {
                    log.warn("标记中止会话失败 sessionId={}: {}", sessionId, ex.getMessage());
                }
            }
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", sessionId);
            data.put("conversationId", conversationId);
            sink.emit("aborted", data);
            sink.complete();
        } catch (Exception e) {
            log.error("流式诊断失败", e);
            sink.emit("error", Map.of("message", "服务暂时不可用，请稍后再试或直接报修"));
            sink.complete();
        }
    }

    /** 最近 3 轮（一次提问 = 一轮），按时间从旧到新返回，直接可注入 prompt */
    private List<DiagnosisSession> loadHistory(Long userId, String conversationId) {
        if (userId == null || conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        try {
            List<DiagnosisSession> rows =
                    diagnosisSessionRepository.findTop3ByConversationIdAndUserIdOrderByGmtCreateDesc(
                            conversationId, userId);
            List<DiagnosisSession> ordered = new ArrayList<>(rows);
            Collections.reverse(ordered);
            return ordered;
        } catch (Exception e) {
            log.warn("读取多轮上下文失败，按无上下文继续: {}", e.getMessage());
            return List.of();
        }
    }

    /** history 按时间从旧到新排列，最后一个才是用户刚刚问的那一轮 */
    private DiagnosisSession latestTurn(List<DiagnosisSession> history) {
        return history.get(history.size() - 1);
    }

    /** 上一轮是否已经明确指向报修（用于"还有其他方法吗"这类追问） */
    private boolean previousSuggestedRepair(List<DiagnosisSession> history) {
        String answer = latestTurn(history).getAnswer();
        return answer != null
                && (answer.contains("报修工单") || answer.contains("建议直接报修")
                    || answer.contains("提交报修"));
    }

    /** 拼给模型.user 消息：问题 + 最近 3 轮 + 已试无效标记 + 唯一依据的 Top-3 条目 */
    private String buildUserPrompt(String question, List<DiagnosisResult.Match> matches,
                                   List<DiagnosisSession> history, boolean triedBefore) {
        StringBuilder sb = new StringBuilder();
        sb.append("【学生问题】\n").append(question).append("\n\n");
        if (!history.isEmpty()) {
            sb.append("【最近对话（从旧到新）】\n");
            for (DiagnosisSession turn : history) {
                sb.append("用户：").append(turn.getQuestion()).append("\n");
                sb.append("助手：").append(abbrev(turn.getAnswer(), 400)).append("\n");
            }
            sb.append("\n");
        }
        sb.append("【反馈】上一轮方法已试过且无效：").append(triedBefore ? "是" : "否").append("\n\n");
        sb.append("【知识条目（唯一依据，不得超出其内容）】\n");
        for (int i = 0; i < matches.size(); i++) {
            DiagnosisResult.Match m = matches.get(i);
            sb.append(i + 1).append(". 标题：").append(m.getTitle()).append("\n");
            sb.append("   标准问题：").append(m.getQuestion()).append("\n");
            sb.append("   解决方案：").append(m.getAnswer()).append("\n");
        }
        return sb.toString();
    }

    /** 上一轮没解决时的专用回复：承认试过、停止重复、指向报修 */
    private String triedBeforeTip(DiagnosisSession previous) {
        List<String> steps = attemptedSteps(List.of(previous), true);
        if (steps.isEmpty()) {
            // 上一轮是兜底文案、没有③步骤时，不要再说"上一轮的方法（上一轮的方法）"
            return "看来上一轮的建议已经试过并且没有效果，我不再重复。"
                    + "这种情况建议直接提交报修工单，由师傅上门检查，同时联系宿管说明情况。";
        }
        String joined = steps.stream().limit(3).reduce((a, b) -> a + "；" + b).orElse("");
        return "看来上一轮的方法（" + abbrev(joined, 160)
                + "）已经试过并且没有效果，我不再重复这些步骤。"
                + "这种情况建议直接提交报修工单，由师傅上门检查，同时联系宿管说明情况。";
    }

    /** 从历史回答的"③分步操作"里抽出用户真正试过的步骤，供 Repair.vue 预填 */
    private List<String> attemptedSteps(List<DiagnosisSession> history, boolean triedBefore) {
        if (!triedBefore || history.isEmpty()) {
            return List.of();
        }
        // 最新一轮可能是"试过了还是不行"的确认语，里面没有③。
        // 从新往旧找第一份带步骤的回答，接力时才能把真正试过的动作带去报修页。
        String answer = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            String candidate = history.get(i).getAnswer();
            if (candidate != null && candidate.contains("③")) {
                answer = candidate;
                break;
            }
        }
        if (answer == null || answer.isBlank()) {
            return List.of();
        }
        int start = answer.indexOf("③");
        if (start < 0) {
            return List.of();
        }
        int end = answer.indexOf("④", start);
        String section = answer.substring(start + 1, end < 0 ? answer.length() : end);
        List<String> steps = new ArrayList<>();
        for (String line : section.split("\\r?\\n")) {
            // 老种子答案是"1.动作；成功判据：…；2.动作；…"一行排开；
            // 新格式是动作单独一行，下一行"↳ 怎么算有效：…"。
            // 两种都要只取动作，判据不能塞进"试过的步骤"里。
            for (String part : line.split("[；;]")) {
                if (part.contains("成功判据") || part.contains("怎么算有效") || part.contains("↳")) {
                    continue;
                }
                String clean = part.trim()
                        .replaceFirst("^(分步操作[:：]\\s*)?", "")
                        .replaceFirst("^[\\d.、\\s]+", "")
                        .replaceAll("[。.\\s]+$", "");
                if (clean.length() >= 4) {
                    steps.add(abbrev(clean, 80));
                }
                if (steps.size() >= 5) {
                    return steps;
                }
            }
        }
        return steps;
    }

    private String abbrev(String text, int max) {
        if (text == null) {
            return "";
        }
        String clean = text.trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }

    /** meta 里只给引用溯源需要的最小字段，别把整段答案重复推一遍 */
    private Map<String, Object> briefMatch(DiagnosisResult.Match m) {
        Map<String, Object> brief = new HashMap<>();
        brief.put("id", m.getId());
        brief.put("title", m.getTitle());
        brief.put("question", m.getQuestion());
        brief.put("similarity", m.getSimilarity());
        return brief;
    }

    /** 追问建议：用其余命中条目的标准问题，零成本制造"连续对话"感 */
    private List<String> buildSuggestions(List<DiagnosisResult.Match> matches, boolean hide) {
        if (hide || matches.size() < 2) {
            return List.of();
        }
        List<String> suggestions = new ArrayList<>();
        matches.stream().skip(1).limit(2).forEach(m -> suggestions.add(m.getQuestion()));
        return suggestions;
    }

    /** 追加一段完整文本并推一个 delta 事件（非流式路径用） */
    private void appendDelta(DiagnosisEventSink sink, StringBuilder fullAnswer, String text) {
        fullAnswer.append(text);
        sink.emit("delta", Map.of("text", text));
    }
}
