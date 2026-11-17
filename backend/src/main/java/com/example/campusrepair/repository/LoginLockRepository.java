package com.example.campusrepair.repository;

import com.example.campusrepair.entity.LoginLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoginLockRepository extends JpaRepository<LoginLock, Long> {

    /** 行锁保证并发密码错误不会互相覆盖失败次数。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LoginLock l where l.userId = :userId and l.lockType = :lockType")
    Optional<LoginLock> findByUserIdAndLockTypeForUpdate(@Param("userId") Long userId,
                                                         @Param("lockType") String lockType);

    Optional<LoginLock> findByUserIdAndLockType(Long userId, String lockType);
}