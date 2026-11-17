package com.example.campusrepair.service;

import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;

/** SMTP 邮箱验证码发送器，负责发送和常见邮件错误分类。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailCodeSender {

    private final JavaMailSender mailSender;

    @Value("${app.email.mock:true}")
    private boolean mock;

    @Value("${app.email.from:no-reply@campus-repair.local}")
    private String from;

    public void send(String email, String code) {
        if (mock) {
            log.info("[EMAIL-MOCK] email={}, code={}", maskEmail(email), code);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(email);
            message.setSubject("校园快修登录验证码");
            message.setText("您的登录验证码是：" + code + "，5 分钟内有效。若非本人操作请忽略。");
            mailSender.send(message);
            log.info("邮箱验证码发送成功，email={}", maskEmail(email));
        } catch (MailAuthenticationException e) {
            throw mailFailure(MailFailure.authenticationFailure(), email, e);
        } catch (MailParseException e) {
            throw mailFailure(MailFailure.messageInvalid(), email, e);
        } catch (MailSendException e) {
            throw mailFailure(classifyMailFailure(e), email, e);
        } catch (MailException e) {
            throw mailFailure(classifyMailFailure(e), email, e);
        } catch (Exception e) {
            throw mailFailure(classifyMailFailure(e), email, e);
        }
    }

    static MailFailure classifyMailFailure(Throwable error) {
        if (error instanceof MailAuthenticationException) return MailFailure.authenticationFailure();
        if (error instanceof MailParseException) return MailFailure.messageInvalid();
        if (hasCause(error, SocketTimeoutException.class)) {
            return MailFailure.timeout();
        }
        if (hasCause(error, SSLHandshakeException.class)) {
            return MailFailure.tlsFailure();
        }
        if (hasCause(error, UnknownHostException.class) || hasCause(error, ConnectException.class)) {
            return MailFailure.connectFailure();
        }
        String message = error == null || error.getMessage() == null
                ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("couldn't connect") || message.contains("connection refused")
                || message.contains("unknown host")) {
            return MailFailure.connectFailure();
        }
        if (message.contains("550") || message.contains("551") || message.contains("553")
                || message.contains("recipient") || message.contains("mailbox")) {
            return MailFailure.recipientRejected();
        }
        return MailFailure.sendFailed();
    }

    private AuthException mailFailure(MailFailure failure, String email, Throwable cause) {
        log.error("邮箱验证码发送失败，email={}, errorCode={}", maskEmail(email), failure.errorCode(), cause);
        return AuthException.of(failure.status(), failure.message(), failure.errorCode(),
                ErrorCode.CHANNEL_EMAIL, failure.retryable(), null);
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        while (current != null) {
            if (type.isInstance(current)) return true;
            current = current.getCause();
        }
        return false;
    }

    public boolean isMock() {
        return mock;
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "unknown";
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    record MailFailure(HttpStatus status, String message, String errorCode, boolean retryable) {

        static MailFailure authenticationFailure() {
            return new MailFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "邮箱服务认证失败，请联系管理员", ErrorCode.EMAIL_SMTP_AUTH_FAILED, false);
        }

        static MailFailure connectFailure() {
            return new MailFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "邮件服务连接失败，请联系管理员", ErrorCode.EMAIL_SMTP_CONNECT_FAILED, true);
        }

        static MailFailure timeout() {
            return new MailFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "邮件服务响应超时，请稍后重试", ErrorCode.EMAIL_SMTP_TIMEOUT, true);
        }

        static MailFailure tlsFailure() {
            return new MailFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "邮件加密配置异常，请联系管理员", ErrorCode.EMAIL_TLS_CONFIG_ERROR, false);
        }

        static MailFailure recipientRejected() {
            return new MailFailure(HttpStatus.BAD_REQUEST,
                    "该邮箱暂时无法接收验证码", ErrorCode.EMAIL_RECIPIENT_REJECTED, false);
        }

        static MailFailure messageInvalid() {
            return new MailFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "邮件内容配置异常，请联系管理员", ErrorCode.EMAIL_MESSAGE_INVALID, false);
        }

        static MailFailure sendFailed() {
            return new MailFailure(HttpStatus.SERVICE_UNAVAILABLE,
                    "验证码邮件发送失败，请稍后重试", ErrorCode.EMAIL_SEND_FAILED, true);
        }
    }
}
