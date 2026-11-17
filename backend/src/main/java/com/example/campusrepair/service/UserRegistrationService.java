package com.example.campusrepair.service;

import com.example.campusrepair.entity.Role;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.entity.UserRole;
import com.example.campusrepair.repository.RoleRepository;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 外部登录方式首次登录注册；用户与 RBAC 角色关联必须在同一事务中完成。 */
@Service
@RequiredArgsConstructor
public class UserRegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;

    @Transactional
    public User register(String username, String realName, String roleCode,
                         String phone, String email, String wxOpenid) {
        // 先确认角色已初始化，避免先插入用户再因角色缺失留下半成品数据。
        Role role = roleRepository.findByCodeAndIsDeleted(roleCode, 0)
                .orElseThrow(() -> new IllegalStateException("RBAC 角色未初始化: " + roleCode));

        User user = new User();
        user.setUsername(username);
        user.setPassword(BCrypt.hashpw(UUID.randomUUID() + "!Aa1", BCrypt.gensalt()));
        user.setRealName(realName);
        user.setRole(roleCode);
        user.setPhone(phone);
        user.setEmail(email);
        user.setWxOpenid(wxOpenid);
        user.setStatus("ACTIVE");
        user.setTokenVersion(0L);
        user.setIsDeleted(0);
        User saved = userRepository.save(user);

        UserRole userRole = new UserRole();
        userRole.setUserId(saved.getId());
        userRole.setRoleId(role.getId());
        userRoleRepository.save(userRole);
        return saved;
    }
}