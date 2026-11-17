package com.example.campusrepair;

import com.example.campusrepair.dto.ClientInfo;
import com.example.campusrepair.entity.LoginLock;
import com.example.campusrepair.entity.Permission;
import com.example.campusrepair.entity.User;
import com.example.campusrepair.entity.Role;
import com.example.campusrepair.entity.UserRole;
import com.example.campusrepair.repository.LoginLockRepository;
import com.example.campusrepair.repository.PermissionRepository;
import com.example.campusrepair.repository.UserRepository;
import com.example.campusrepair.repository.RoleRepository;
import com.example.campusrepair.repository.UserRoleRepository;
import com.example.campusrepair.service.AccountLockService;
import com.example.campusrepair.service.AuthAuditService;
import com.example.campusrepair.service.AuthCodeService;
import com.example.campusrepair.service.TokenSessionService;
import com.example.campusrepair.service.PermissionService;
import com.example.campusrepair.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 不依赖数据库的认证核心单元测试。 */
class CampusRepairApplicationTests {

    @Test
    void jwtShouldDistinguishAccessAndRefreshTokens() {
        JwtUtil jwtUtil = new JwtUtil("01234567890123456789012345678901", 30, 7);
        JwtUtil.IssuedToken access = jwtUtil.issueAccessToken(88L, "session-1");
        JwtUtil.IssuedToken refresh = jwtUtil.issueRefreshToken(88L, "session-1");

        Claims accessClaims = jwtUtil.parse(access.token());
        Claims refreshClaims = jwtUtil.parse(refresh.token());

        assertNotNull(accessClaims);
        assertNotNull(refreshClaims);
        assertEquals("access", accessClaims.get("type", String.class));
        assertEquals("refresh", refreshClaims.get("type", String.class));
        assertEquals("88", accessClaims.getSubject());
        assertEquals("session-1", accessClaims.get("sid", String.class));
    }

    @Test
    void captchaAndSmsCodeShouldBeOneTimeCredentials() {
        AuthCodeService service = new AuthCodeService();
        service.saveCaptcha("captcha-1", "AB12");
        assertTrue(service.verifyCaptcha("captcha-1", "ab12"));
        assertFalse(service.verifyCaptcha("captcha-1", "ab12"));

        String code = service.generateSmsCode("13800000000");
        assertFalse(service.verifySmsCode("13800000000", "000000".equals(code) ? "111111" : "000000"));
        assertTrue(service.verifySmsCode("13800000000", code));
        assertFalse(service.verifySmsCode("13800000000", code));
    }

    @Test
    void wildcardRoleShouldStillExposeAllMenus() {
        UserRoleRepository userRoleRepository = mock(UserRoleRepository.class);
        RoleRepository roleRepository = mock(RoleRepository.class);
        PermissionRepository permissionRepository = mock(PermissionRepository.class);
        PermissionService service = new PermissionService(userRoleRepository, roleRepository, permissionRepository);

        UserRole relation = new UserRole();
        relation.setUserId(1L);
        relation.setRoleId(1L);
        Role admin = new Role();
        admin.setId(1L);
        admin.setCode("ADMIN");
        admin.setIsDeleted(0);
        Permission wildcard = permission(40L, "*:*", "API");
        Permission menu = permission(1L, "menu:dashboard", "MENU");
        menu.setRoutePath("/dashboard");

        when(userRoleRepository.findByUserId(1L)).thenReturn(List.of(relation));
        when(roleRepository.findAllById(List.of(1L))).thenReturn(List.of(admin));
        when(permissionRepository.findEnabledByRoleIds(List.of(1L))).thenReturn(List.of(wildcard));
        when(permissionRepository.findByIsDeletedOrderBySortNoAscIdAsc(0))
                .thenReturn(List.of(menu, wildcard));

        PermissionService.AccessProfile profile = service.loadProfile(1L);
        assertTrue(profile.permissions().contains("*:*"));
        assertEquals(1, profile.menus().size());
        assertEquals("menu:dashboard", profile.menus().get(0).code());
    }

    private static Permission permission(Long id, String code, String type) {
        Permission permission = new Permission();
        permission.setId(id);
        permission.setCode(code);
        permission.setName(code);
        permission.setType(type);
        permission.setSortNo(0);
        permission.setIsDeleted(0);
        return permission;
    }
    @Test
    void fifthPasswordFailureShouldLockUserAndRevokeSessions() {
        LoginLockRepository lockRepository = mock(LoginLockRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        TokenSessionService tokenSessionService = mock(TokenSessionService.class);
        AuthAuditService auditService = mock(AuthAuditService.class);
        AccountLockService service = new AccountLockService(lockRepository, userRepository,
                tokenSessionService, auditService);
        ReflectionTestUtils.setField(service, "maxLoginFail", 5);
        ReflectionTestUtils.setField(service, "lockMinutes", 10);

        User user = new User();
        user.setId(9L);
        user.setStatus("ACTIVE");
        LoginLock[] stored = {null};
        when(lockRepository.findByUserIdAndLockTypeForUpdate(9L, LoginLock.TYPE_PASSWORD))
                .thenAnswer(invocation -> Optional.ofNullable(stored[0]));
        when(lockRepository.save(any(LoginLock.class))).thenAnswer(invocation -> {
            LoginLock lock = invocation.getArgument(0);
            if (lock.getId() == null) {
                lock.setId(1L);
            }
            stored[0] = lock;
            return lock;
        });
        ClientInfo client = new ClientInfo("127.0.0.1", "test", "fingerprint");

        for (int attempt = 1; attempt <= 4; attempt++) {
            AccountLockService.FailureOutcome outcome = service.recordPasswordFailure(user, client);
            assertFalse(outcome.locked());
            assertEquals(5 - attempt, outcome.remainingAttempts());
        }
        AccountLockService.FailureOutcome locked = service.recordPasswordFailure(user, client);

        assertTrue(locked.locked());
        assertEquals("LOCKED", user.getStatus());
        assertEquals(LoginLock.STATUS_ACTIVE, stored[0].getStatus());
        assertTrue(locked.lockRemainingMinutes() >= 9);
        verify(tokenSessionService).revokeAll(9L, "ACCOUNT_LOCKED");
    }}