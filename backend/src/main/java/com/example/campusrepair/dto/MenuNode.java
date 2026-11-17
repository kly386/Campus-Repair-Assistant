package com.example.campusrepair.dto;

import java.util.List;

/** 前端动态路由使用的菜单树节点。 */
public record MenuNode(Long id, String code, String name, String path, String component,
                       String icon, Integer sortNo, List<MenuNode> children) {
}