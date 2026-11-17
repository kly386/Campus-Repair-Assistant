package com.example.campusrepair.service;

import com.example.campusrepair.entity.Permission;
import com.example.campusrepair.entity.Role;
import com.example.campusrepair.entity.RolePermission;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.entity.UserRole;
import com.example.campusrepair.exception.AuthException;
import com.example.campusrepair.repository.PermissionRepository;
import com.example.campusrepair.repository.RolePermissionRepository;
import com.example.campusrepair.repository.RoleRepository;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * RBAC 管理服务。
 *
 * <p>角色或权限变更后立即吊销受影响用户的全部 Token，用户重新登录即可拿到新权限；
 * 即使旧 Token 尚未过期也无法继续访问，满足禅道式“权限实时校验 + 会话绑定”。</p>
 */
@Service
@RequiredArgsConstructor
public class RbacAdminService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final TokenSessionService tokenSessionService;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    public List<Role> roles() {
        return roleRepository.findByIsDeletedOrderByIdAsc(0);
    }

    @Transactional(readOnly = true)
    public List<Permission> permissions() {
        return permissionRepository.findByIsDeletedOrderBySortNoAscIdAsc(0);
    }

    /** 替换用户角色集合，并吊销用户全部会话。 */
    @Transactional
    public void assignUserRoles(Long targetUserId, Set<Long> roleIds, Long operatorId, String ip) {
        User user = userRepository.findByIdAndIsDeleted(targetUserId, 0)
                .orElseThrow(() -> AuthException.badRequest("用户不存在"));
        Set<Long> normalized = roleIds == null ? Set.of() : new HashSet<>(roleIds);
        List<Role> roles = roleRepository.findAllById(normalized);
        if (roles.size() != normalized.size()
                || roles.stream().anyMatch(r -> !Integer.valueOf(0).equals(r.getIsDeleted()))) {
            throw AuthException.badRequest("包含无效角色");
        }
        userRoleRepository.deleteByUserId(targetUserId);
        for (Role role : roles) {
            UserRole userRole = new UserRole();
            userRole.setUserId(targetUserId);
            userRole.setRoleId(role.getId());
            userRoleRepository.save(userRole);
        }
        user.setRole(roles.isEmpty() ? user.getRole() : roles.get(0).getCode());
        userRepository.save(user);
        int revoked = tokenSessionService.revokeAll(targetUserId, "ROLE_CHANGED");
        auditService.audit("USER_ROLE_CHANGED", operatorId, targetUserId, null,
                "用户角色更新为 " + normalized + "，吊销会话 " + revoked + " 个", ip);
    }

    /** 替换角色权限集合，并吊销该角色下全部用户的会话。 */
    @Transactional
    public void assignRolePermissions(Long roleId, Set<Long> permissionIds, Long operatorId, String ip) {
        Role role = roleRepository.findById(roleId)
                .filter(r -> Integer.valueOf(0).equals(r.getIsDeleted()))
                .orElseThrow(() -> AuthException.badRequest("角色不存在"));
        Set<Long> normalized = permissionIds == null ? Set.of() : new HashSet<>(permissionIds);
        List<Permission> permissions = permissionRepository.findAllById(normalized);
        if (permissions.size() != normalized.size()
                || permissions.stream().anyMatch(p -> !Integer.valueOf(0).equals(p.getIsDeleted()))) {
            throw AuthException.badRequest("包含无效权限");
        }

        rolePermissionRepository.deleteByRoleId(roleId);
        for (Permission permission : permissions) {
            RolePermission relation = new RolePermission();
            relation.setRoleId(roleId);
            relation.setPermissionId(permission.getId());
            rolePermissionRepository.save(relation);
        }
        List<Long> userIds = userRoleRepository.findByRoleId(roleId).stream()
                .map(UserRole::getUserId)
                .distinct()
                .toList();
        int revokedTotal = 0;
        for (Long userId : userIds) {
            revokedTotal += tokenSessionService.revokeAll(userId, "PERMISSION_CHANGED");
        }
        auditService.audit("ROLE_PERMISSION_CHANGED", operatorId, null, roleId,
                "角色 " + role.getCode() + " 权限更新为 " + normalized
                        + "，吊销会话 " + revokedTotal + " 个", ip);
    }
}