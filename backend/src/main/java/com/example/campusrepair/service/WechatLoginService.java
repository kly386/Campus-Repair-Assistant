package com.example.campusrepair.service;

import com.example.campusrepair.exception.AuthException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 微信扫码登录会话服务。
 *
 * <p>mock 模式提供完整二维码、扫码、确认、轮询、过期演示链路，并始终绑定固定测试
 * openid。生产模式生成微信开放平台 qrconnect 授权地址，用户扫码后微信回调
 * {@code /api/auth/wx/callback}，服务端用 code 换取 openid 并把 state 标记为 CONFIRMED，
 * 前端轮询随后拿到 Token。二维码 3 分钟过期，且确认状态只能消费一次。</p>
 */
@Service
public class WechatLoginService {

    private static final String WECHAT_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    private final AuthCodeService authCodeService;
    private final ObjectMapper objectMapper;
    private final boolean mock;
    private final String appId;
    private final String appSecret;
    private final String redirectUri;
    private final String mockOpenid;

    public WechatLoginService(AuthCodeService authCodeService,
                              ObjectMapper objectMapper,
                              @Value("${app.wechat.mock:true}") boolean mock,
                              @Value("${app.wechat.app-id:}") String appId,
                              @Value("${app.wechat.app-secret:}") String appSecret,
                              @Value("${app.wechat.redirect-uri:}") String redirectUri,
                              @Value("${app.wechat.mock-openid:wx_mock_demo_user}") String mockOpenid) {
        this.authCodeService = authCodeService;
        this.objectMapper = objectMapper;
        this.mock = mock;
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
        this.mockOpenid = mockOpenid;
    }

    public Map<String, Object> createQrSession() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        authCodeService.createWxSession(uuid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uuid", uuid);
        data.put("mock", mock);
        data.put("expiresInSeconds", 180);
        data.put("expiresIn", 180);
        if (mock) {
            data.put("url", "https://open.weixin.qq.com/mock/oauth2?state=" + uuid);
            data.put("qrcodeContent", "MOCK-WX-LOGIN:" + uuid);
        } else {
            if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)
                    || !StringUtils.hasText(redirectUri)) {
                throw new IllegalStateException("生产微信登录缺少 app-id、app-secret 或 redirect-uri");
            }
            String redirect = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
            data.put("url", "https://open.weixin.qq.com/connect/qrconnect?appid=" + appId
                    + "&redirect_uri=" + redirect + "&response_type=code&scope=snsapi_login&state="
                    + uuid + "#wechat_redirect");
        }
        return data;
    }

    public Map<String, Object> poll(String uuid) {
        String status = authCodeService.peekWxState(uuid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", status);
        data.put("expired", AuthCodeService.WX_EXPIRED.equals(status));
        data.put("remainingSeconds", authCodeService.wxRemainingSeconds(uuid));
        return data;
    }

    public void markScanned(String uuid) {
        authCodeService.markWxScanned(uuid);
    }

    /** 本地模拟确认，仅 mock 模式开放。 */
    public void markConfirmed(String uuid) {
        requireMock();
        authCodeService.markWxConfirmed(uuid, mockOpenid);
    }

    /**
     * 微信授权回调：校验 state 未过期，用一次性 code 换取 openid，然后唤醒前端轮询。
     */
    public void handleCallback(String state, String code) {
        if (!StringUtils.hasText(state) || !StringUtils.hasText(code)) {
            throw AuthException.badRequest("微信回调参数不完整");
        }
        String status = authCodeService.peekWxState(state);
        if (AuthCodeService.WX_EXPIRED.equals(status)) {
            throw AuthException.badRequest("二维码已过期，请重新获取");
        }
        String openid = mock ? mockOpenid : exchangeOpenid(code);
        authCodeService.markWxConfirmed(state, openid);
    }

    public String consumeConfirmed(String uuid) {
        return authCodeService.consumeWxConfirmed(uuid);
    }

    private String exchangeOpenid(String code) {
        String url = UriComponentsBuilder.fromHttpUrl(WECHAT_TOKEN_URL)
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("code", code)
                .queryParam("grant_type", "authorization_code")
                .encode()
                .toUriString();
        try {
            String body = RestClient.create().get().uri(url).retrieve().body(String.class);
            JsonNode json = objectMapper.readTree(body);
            if (json == null || !json.hasNonNull("openid")) {
                throw new AuthException(org.springframework.http.HttpStatus.BAD_GATEWAY, 502,
                        "微信授权失败，请重新扫码");
            }
            return json.get("openid").asText();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException(org.springframework.http.HttpStatus.BAD_GATEWAY, 502,
                    "微信服务暂时不可用，请稍后再试");
        }
    }

    private void requireMock() {
        if (!mock) {
            throw AuthException.badRequest("生产模式请通过微信官方二维码确认登录");
        }
    }

    public boolean isMock() {
        return mock;
    }
}