package com.perm.admin.controller;

import com.perm.admin.service.PermissionService;
import com.perm.admin.service.PermissionService.QueryRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 跨库/跨表查询权限，返回打平行列表 + errors。 */
    @PostMapping("/query")
    public Map<String, Object> query(@RequestBody QueryRequest req) {
        return permissionService.query(req);
    }
}
