package com.example.campusrepair.service;

import com.example.campusrepair.dto.MenuNode;
import com.example.campusrepair.entity.Permission;
import com.example.campusrepair.entity.Role;
import com.example.campusrepair.entity.UserRole;
import com.example.campusrepair.repository.PermissionRepository;
import com.example.campusrepair.repository.RoleRepository;
import com.example.campusrepair.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 动态权限计算服务。
 *
 * <p>用户每次请求的权限都通过 user_role -> role_permission -> permission 实时计算，
 * 不依赖 JWT 中的旧权限快照。菜单树和安全码（API/BUTTON）来自同一权限表。</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Transactional(readOnly = true)
    public AccessProfile loadProfile(Long userId) {
        List<Long> roleIds = userRoleRepository.findByUserId(userId).stream()
                .map(UserRole::getRoleId)
                .distinct()
                .toList();
        if (roleIds.isEmpty()) {
            return new AccessProfile(Set.of(), Set.of(), List.of());
        }

        Set<String> roleCodes = roleRepository.findAllById(roleIds).stream()
                .filter(role -> Integer.valueOf(0).equals(role.getIsDeleted()))
                .map(Role::getCode)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        List<Permission> permissions = permissionRepository.findEnabledByRoleIds(roleIds);
        if (permissions.stream().anyMatch(permission -> "*:*".equals(permission.getCode()))) {
            // *:* 超级权限只补齐 API/BUTTON 安全码（接口全通）；
            // 菜单仍按角色显式授予，避免管理员导航混入各角色专属页面（如师傅接单工作台）。
            Set<Long> grantedIds = permissions.stream()
                    .map(Permission::getId)
                    .collect(java.util.stream.Collectors.toSet());
            List<Permission> extras = permissionRepository.findByIsDeletedOrderBySortNoAscIdAsc(0).stream()
                    .filter(permission -> !"MENU".equalsIgnoreCase(permission.getType()))
                    .filter(permission -> !grantedIds.contains(permission.getId()))
                    .toList();
            permissions = new ArrayList<>(permissions);
            permissions.addAll(extras);
        }
        Set<String> permissionCodes = permissions.stream()
                .map(Permission::getCode)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        return new AccessProfile(roleCodes, permissionCodes, buildMenuTree(permissions));
    }

    @Transactional(readOnly = true)
    public Set<String> permissionCodes(Long userId) {
        return loadProfile(userId).permissions();
    }

    @Transactional(readOnly = true)
    public Set<String> roleCodes(Long userId) {
        return loadProfile(userId).roles();
    }

    @Transactional(readOnly = true)
    public List<Permission> allPermissions() {
        return permissionRepository.findByIsDeletedOrderBySortNoAscIdAsc(0);
    }

    /** 构建 MENU 类型权限树；API/BUTTON 权限不进入前端路由。 */
    public List<MenuNode> buildMenuTree(Collection<Permission> permissions) {
        List<Permission> menus = permissions.stream()
                .filter(permission -> "MENU".equalsIgnoreCase(permission.getType()))
                .toList();
        Set<Long> menuIds = new HashSet<>();
        menus.forEach(menu -> menuIds.add(menu.getId()));

        Map<Long, List<Permission>> children = new HashMap<>();
        List<Permission> roots = new ArrayList<>();
        for (Permission menu : menus) {
            if (menu.getParentId() == null || !menuIds.contains(menu.getParentId())) {
                roots.add(menu);
            } else {
                children.computeIfAbsent(menu.getParentId(), ignored -> new ArrayList<>()).add(menu);
            }
        }
        Comparator<Permission> order = Comparator
                .comparing(Permission::getSortNo, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Permission::getId);
        roots.sort(order);
        return roots.stream().map(root -> toMenuNode(root, children, order)).toList();
    }

    private MenuNode toMenuNode(Permission permission,
                                Map<Long, List<Permission>> children,
                                Comparator<Permission> order) {
        List<Permission> childPermissions = children.getOrDefault(permission.getId(), List.of());
        List<MenuNode> childNodes = childPermissions.stream()
                .sorted(order)
                .map(child -> toMenuNode(child, children, order))
                .toList();
        return new MenuNode(permission.getId(), permission.getCode(), permission.getName(),
                permission.getRoutePath(), permission.getComponent(), permission.getIcon(),
                permission.getSortNo(), childNodes);
    }

    public record AccessProfile(Set<String> roles, Set<String> permissions, List<MenuNode> menus) {
    }
}