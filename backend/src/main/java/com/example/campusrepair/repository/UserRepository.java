package com.example.campusrepair.repository;

import com.example.campusrepair.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问
 *
 * @Description 支持按 登录名 / 手机号 / 微信openid 三种方式检索用户
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 按登录名查有效用户（isDeleted=0 表示未删除） */
    Optional<User> findByUsernameAndIsDeleted(String username, Integer isDeleted);

    /** 按手机号查有效用户（短信登录） */
    Optional<User> findByPhoneAndIsDeleted(String phone, Integer isDeleted);

    /** 按微信 openid 查有效用户（扫码登录） */
    Optional<User> findByWxOpenidAndIsDeleted(String wxOpenid, Integer isDeleted);
}
