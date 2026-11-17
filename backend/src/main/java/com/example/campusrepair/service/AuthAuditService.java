package com.example.campusrepair.service;

import com.example.campusrepair.dto.ClientInfo;
import com.example.campusrepair.entity.AuthAuditLog;
import com.example.campusrepair.entity.LoginRecord;
import com.example.campusrepair.repository.AuthAuditLogRepository;
import com.example.campusrepair.repository.LoginRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 登录审计与安全事件审计。独立事务写入，避免业务异常回滚导致日志丢失。 */
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final LoginRecordRepository loginRecordRepository;
    private final AuthAuditLogRepository auditLogRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(Long userId, String loginType, String identifier, ClientInfo client,
                            boolean success, String failureReason) {
        LoginRecord record = new LoginRecord();
        record.setUserId(userId);
        record.setLoginType(loginType);
        record.setIdentifier(trim(identifier, 100));
        record.setIp(client == null ? null : trim(client.ip(), 50));
        record.setUserAgent(client == null ? null : trim(client.userAgent(), 500));
        record.setClientFingerprint(client == null ? null : client.fingerprint());
        record.setSuccess(success);
        record.setFailureReason(trim(failureReason, 200));
        loginRecordRepository.save(record);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String eventType, Long operatorId, Long targetUserId, Long targetId,
                      String detail, String ip) {
        AuthAuditLog log = new AuthAuditLog();
        log.setEventType(eventType);
        log.setOperatorId(operatorId);
        log.setTargetUserId(targetUserId);
        log.setTargetId(targetId);
        log.setDetail(trim(detail, 1000));
        log.setIp(trim(ip, 50));
        auditLogRepository.save(log);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}