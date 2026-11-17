package com.example.campusrepair.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口级权限标识。
 *
 * <p>拦截器读取该注解并查询当前用户的动态权限集合；只隐藏前端按钮而不加此注解，
 * 直接调用接口仍会越权，因此业务接口必须显式声明权限。</p>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** permission.code，例如 order:create、system:user:role。 */
    String value();
}