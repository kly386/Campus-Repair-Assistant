package com.example.campusrepair.service;

import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/** Spug 真实短信发送器，负责接口调用、错误分类和敏感信息脱敏。 */
@Slf4j
@Service
public class SmsCodeSender {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean mock;
    private final String spugUrl;
    private final String validNumber;

    public SmsCodeSender(RestTemplateBuilder restTemplateBuilder,
                         ObjectMapper objectMapper,
                         @Value("${app.sms.mock:true}") boolean mock,
                         @Value("${app.sms.spug.url:}") String spugUrl,
                         @Value("${app.sms.spug.number:5}") String validNumber) {
        this.objectMapper = objectMapper;
        this.mock = mock;
        this.spugUrl = spugUrl;
        this.validNumber = validNumber;
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
        if (!mock && !StringUtils.hasText(spugUrl)) {
            throw new IllegalStateException("真实短信模式缺少 SPUG_SMS_URL 配置");
        }
    }

    public void send(String phone, String code) {
        if (mock) {
            log.info("[SMS-MOCK] phone={}, code={}", maskPhone(phone), code);
            return;
        }
        URI requestUri = UriComponentsBuilder.fromHttpUrl(spugUrl)
                .queryParam("to", phone)
                .queryParam("code", code)
                .queryParam("number", validNumber)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        try {
            String body = restTemplate.getForObject(requestUri, String.class);
            JsonNode json = body == null ? null : objectMapper.readTree(body);
            int responseCode = json == null ? -1 : json.path("code").asInt(-1);
            if (responseCode != HttpStatus.OK.value()) {
                String providerMessage = json == null ? "empty response" : json.path("msg").asText("unknown error");
                String requestId = json == null ? "" : json.path("request_id").asText("");
                ProviderFailure failure = classifySpugFailure(providerMessage);
                log.error("Spug 短信发送失败，phone={}, providerCode={}, providerMessage={}, requestId={}, errorCode={}",
                        maskPhone(phone), responseCode, providerMessage, requestId, failure.errorCode());
                throw AuthException.of(failure.status(), failure.message(), failure.errorCode(),
                        ErrorCode.CHANNEL_SMS, failure.retryable(), null);
            }
            log.info("Spug 短信发送成功，phone={}", maskPhone(phone));
        } catch (AuthException e) {
            throw e;
        } catch (RestClientException e) {
            ProviderFailure failure = classifyTransportFailure(e);
            log.error("Spug 短信接口调用异常，phone={}, errorCode={}", maskPhone(phone), failure.errorCode(), e);
            throw AuthException.of(failure.status(), failure.message(), failure.errorCode(),
                    ErrorCode.CHANNEL_SMS, failure.retryable(), null);
        } catch (Exception e) {
            log.error("Spug 短信响应解析失败，phone={}", maskPhone(phone), e);
            throw AuthException.of(HttpStatus.SERVICE_UNAVAILABLE, "验证码发送失败，请稍后重试",
                    ErrorCode.SMS_SEND_FAILED, ErrorCode.CHANNEL_SMS, true, null);
        }
    }

    static ProviderFailure classifySpugFailure(String providerMessage) {
        String message = providerMessage == null ? "" : providerMessage.toLowerCase(Locale.ROOT);
        if (containsAny(message, "额度", "余额", "余量", "不足", "quota", "balance")) {
            return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "短信服务额度已用完，请联系管理员充值后再试",
                    ErrorCode.SMS_QUOTA_EXHAUSTED, false);
        }
        if (containsAny(message, "ip", "白名单", "whitelist")) {
            return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "短信服务访问受限，请联系管理员检查服务器 IP 白名单",
                    ErrorCode.SMS_IP_NOT_ALLOWED, false);
        }
        if (containsAny(message, "频率", "频繁", "限制", "rate", "frequency")) {
            return new ProviderFailure(HttpStatus.TOO_MANY_REQUESTS,
                    "短信平台触发频率限制，请稍后再试",
                    ErrorCode.SMS_PROVIDER_RATE_LIMITED, true);
        }
        if (containsAny(message, "模板", "签名", "通道", "template", "sign", "channel")) {
            return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "短信服务配置异常，请联系管理员",
                    ErrorCode.SMS_TEMPLATE_CONFIG_ERROR, false);
        }
        return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                "验证码发送失败，请稍后重试", ErrorCode.SMS_SEND_FAILED, true);
    }

    private ProviderFailure classifyTransportFailure(Throwable error) {
        if (hasCause(error, SocketTimeoutException.class)) {
            return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "短信服务响应超时，请稍后重试", ErrorCode.SMS_PROVIDER_TIMEOUT, true);
        }
        if (hasCause(error, UnknownHostException.class) || hasCause(error, ConnectException.class)) {
            return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "短信服务暂时不可用，请稍后重试", ErrorCode.SMS_PROVIDER_UNAVAILABLE, true);
        }
        return new ProviderFailure(HttpStatus.SERVICE_UNAVAILABLE,
                "短信服务暂时不可用，请稍后重试", ErrorCode.SMS_PROVIDER_UNAVAILABLE, true);
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsAny(String message, String... keywords) {
        for (String keyword : keywords) {
            if (message.contains(keyword)) return true;
        }
        return false;
    }

    public boolean isMock() {
        return mock;
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "unknown";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    record ProviderFailure(HttpStatus status, String message, String errorCode, boolean retryable) {
    }
}