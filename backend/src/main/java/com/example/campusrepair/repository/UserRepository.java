package com.example.campusrepair.repository;

import com.example.campusrepair.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** 用户数据访问；四种登录方式分别按用户名、手机号、邮箱、微信 openid 检索。 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameAndIsDeleted(String username, Integer isDeleted);

    Optional<User> findByPhoneAndIsDeleted(String phone, Integer isDeleted);

    Optional<User> findByEmailAndIsDeleted(String email, Integer isDeleted);

    Optional<User> findByWxOpenidAndIsDeleted(String wxOpenid, Integer isDeleted);

    Optional<User> findByIdAndIsDeleted(Long id, Integer isDeleted);

    boolean existsByUsername(String username);
}