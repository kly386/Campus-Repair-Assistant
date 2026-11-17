package com.example.campusrepair.repository;

import com.example.campusrepair.entity.AuthSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from AuthSession s where s.id = :id")
    Optional<AuthSession> findByIdForUpdate(@Param("id") String id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuthSession s
               set s.revokedAt = :revokedAt, s.revokedReason = :reason
             where s.userId = :userId and s.revokedAt is null
            """)
    int revokeAllByUserId(@Param("userId") Long userId,
                          @Param("revokedAt") LocalDateTime revokedAt,
                          @Param("reason") String reason);

    long countByUserIdAndRevokedAtIsNull(Long userId);
}