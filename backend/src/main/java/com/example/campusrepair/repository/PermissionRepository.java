package com.example.campusrepair.repository;

import com.example.campusrepair.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCodeAndIsDeleted(String code, Integer isDeleted);

    List<Permission> findByIsDeletedOrderBySortNoAscIdAsc(Integer isDeleted);

    /**
     * 动态权限查询：不把权限写死在 JWT 中，每次请求都从角色关联表实时读取，
     * 因此后台调整权限后可以立即生效。
     */
    @Query("""
            select distinct p from Permission p, RolePermission rp
            where p.id = rp.permissionId
              and rp.roleId in :roleIds
              and p.isDeleted = 0
            order by p.sortNo asc, p.id asc
            """)
    List<Permission> findEnabledByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}