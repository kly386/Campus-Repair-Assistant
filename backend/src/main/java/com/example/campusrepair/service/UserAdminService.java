package com.example.campusrepair.service;

import com.example.campusrepair.dto.UserUpdateRequest;
import com.example.campusrepair.dto.UserView;
import com.example.campusrepair.entity.Role;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.entity.UserRole;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.repository.RoleRepository;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户管理服务：列表查询与基本信息维护。
 *
 * <p>密码不在此处修改；角色分配、锁定/解锁沿用 RbacAdminService 的审计与会话吊销逻辑。</p>
 *
 * @Author 开璐瑶
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "LOCKED", "DISABLED");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final TokenSessionService tokenSessionService;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    public List<UserView> users(String keyword, String role, String status) {
        Map<Long, Role> roles = roleMap();
        Map<Long, List<UserRole>> userRoles = userRoleMap();
        return userRepository.findAll().stream()
                .filter(u -> u.getIsDeleted() == null || u.getIsDeleted() == 0)
                .filter(u -> !StringUtils.hasText(keyword)
                        || u.getUsername().toLowerCase().contains(keyword.toLowerCase())
                        || (u.getRealName() != null && u.getRealName().contains(keyword))
                        || (u.getPhone() != null && u.getPhone().contains(keyword)))
                .filter(u -> !StringUtils.hasText(role) || role.equalsIgnoreCase(u.getRole()))
                .filter(u -> !StringUtils.hasText(status) || status.equalsIgnoreCase(u.getStatus()))
                .sorted(Comparator.comparing(User::getId))
                .map(u -> toView(u, roles, userRoles))
                .toList();
    }

    @Transactional
    public UserView update(Long id, UserUpdateRequest req, Long operatorId, String ip) {
        User user = userRepository.findByIdAndIsDeleted(id, 0)
                .orElseThrow(() -> AuthException.badRequest("用户不存在"));
        String beforeStatus = user.getStatus();

        if (StringUtils.hasText(req.getStatus())) {
            String status = req.getStatus().toUpperCase();
            if (!STATUSES.contains(status)) {
                throw AuthException.badRequest("状态只能是 ACTIVE / LOCKED / DISABLED");
            }
            user.setStatus(status);
        }
        if (StringUtils.hasText(req.getRealName())) {
            user.setRealName(req.getRealName());
        }
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setBuilding(req.getBuilding());
        user.setRoom(req.getRoom());
        user = userRepository.save(user);

        if (!beforeStatus.equalsIgnoreCase(user.getStatus()) && !"ACTIVE".equals(user.getStatus())) {
            tokenSessionService.revokeAll(id, "USER_DISABLED");
        }
        auditService.audit("USER_UPDATED", operatorId, id, null,
                "管理员更新用户信息，状态 " + beforeStatus + " -> " + user.getStatus(), ip);
        return toView(user, roleMap(), userRoleMap());
    }

    // ------------------------------------------------------------------

    private UserView toView(User u, Map<Long, Role> roles, Map<Long, List<UserRole>> userRoles) {
        UserView v = new UserView();
        v.setId(u.getId());
        v.setUsername(u.getUsername());
        v.setRealName(u.getRealName());
        v.setRole(u.getRole());
        v.setPhone(u.getPhone());
        v.setEmail(u.getEmail());
        v.setBuilding(u.getBuilding());
        v.setRoom(u.getRoom());
        v.setStatus(u.getStatus());
        v.setLastLoginAt(u.getLastLoginAt());
        v.setGmtCreate(u.getGmtCreate());
        for (UserRole ur : userRoles.getOrDefault(u.getId(), List.of())) {
            Role role = roles.get(ur.getRoleId());
            if (role != null && Integer.valueOf(0).equals(role.getIsDeleted())) {
                v.getRoleIds().add(role.getId());
                v.getRoleNames().add(role.getName());
            }
        }
        return v;
    }

    private Map<Long, Role> roleMap() {
        Map<Long, Role> map = new HashMap<>();
        for (Role r : roleRepository.findAll()) {
            map.put(r.getId(), r);
        }
        return map;
    }

    private Map<Long, List<UserRole>> userRoleMap() {
        Map<Long, List<UserRole>> map = new LinkedHashMap<>();
        for (UserRole ur : userRoleRepository.findAll()) {
            map.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>()).add(ur);
        }
        return map;
    }
}
