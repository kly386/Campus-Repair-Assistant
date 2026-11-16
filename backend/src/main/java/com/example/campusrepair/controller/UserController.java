package com.example.campusrepair.controller;

import com.example.campusrepair.config.AuthContext;
import com.example.campusrepair.dto.ApiResponse;
import com.example.campusrepair.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 当前用户信息接口（拦截器保护下的最小验证样例）
 *
 * @Description 前端登录后调用，展示"token 校验通过 + 从 token 解析用户"链路通。RBAC 时同接口返回权限集。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @GetMapping("/me")
    public ApiResponse<Map<String, Object>> me() {
        Long userId = AuthContext.userId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", userId);
        userRepository.findById(userId).ifPresent(u -> {
            data.put("username", u.getUsername());
            data.put("realName", u.getRealName());
            data.put("role", u.getRole());
            data.put("phone", u.getPhone());
        });
        return ApiResponse.success(data);
    }
}
