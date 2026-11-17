package com.example.campusrepair.repository;

import com.example.campusrepair.entity.DiagnosisSession;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 自助诊断会话数据访问
 *
 * @Author 开璐瑶
 * @Date 2026-11-17
 * @Version 1.0
 */
public interface DiagnosisSessionRepository extends JpaRepository<DiagnosisSession, Long> {
}
