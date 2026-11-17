package com.example.campusrepair.service;

import com.example.campusrepair.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.MailSendException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EmailCodeSenderTest {

    @Test
    void shouldClassifyAuthenticationFailure() {
        EmailCodeSender.MailFailure failure = EmailCodeSender.classifyMailFailure(
                new MailAuthenticationException("bad credentials"));
        assertEquals(ErrorCode.EMAIL_SMTP_AUTH_FAILED, failure.errorCode());
        assertFalse(failure.retryable());
    }

    @Test
    void shouldClassifyMessageParseFailure() {
        EmailCodeSender.MailFailure failure = EmailCodeSender.classifyMailFailure(
                new MailParseException("invalid message"));
        assertEquals(ErrorCode.EMAIL_MESSAGE_INVALID, failure.errorCode());
    }

    @Test
    void shouldClassifyRecipientRejection() {
        EmailCodeSender.MailFailure failure = EmailCodeSender.classifyMailFailure(
                new MailSendException("550 mailbox unavailable"));
        assertEquals(ErrorCode.EMAIL_RECIPIENT_REJECTED, failure.errorCode());
        assertEquals(400, failure.status().value());
    }
}