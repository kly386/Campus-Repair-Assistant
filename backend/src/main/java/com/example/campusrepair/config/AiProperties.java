package com.example.campusrepair.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 公共配置
 *
 * @Description 多模态报修解析与 RAG 问答共用的模型访问参数（OpenAI 兼容协议）。
 *              mock=true 或 api-key 为空时上层客户端走本地模拟，不产生任何外呼；
 *              密钥一律经环境变量 AI_API_KEY 注入，禁止写入仓库。
 * @Author 伍梦圆
 * @Date 2026-11-17
 * @Version 1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** OpenAI 兼容服务地址（以 /v1 结尾，客户端自动拼接 /chat/completions） */
    private String baseUrl;

    /** 模型 API Key，生产/真实调用必须由环境变量 AI_API_KEY 注入 */
    private String apiKey;

    /** 模型名，如 qwen-vl-plus / deepseek-chat */
    private String model;

    /** 单次调用超时毫秒数 */
    private long timeoutMs = 30000;

    /** true=本地模拟（不外呼），联调与演示用 */
    private boolean mock = true;

    /** 单次解析最多携带图片数 */
    private int maxImages = 3;

    /** 是否可用真实调用：非 mock 且已配置 key */
    public boolean realReady() {
        return !mock && apiKey != null && !apiKey.isBlank();
    }
}
