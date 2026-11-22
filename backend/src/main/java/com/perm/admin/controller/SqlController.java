package com.perm.admin.controller;

import com.perm.admin.service.PermissionService;
import com.perm.admin.service.PermissionService.ExecRequest;
import com.perm.admin.service.PermissionService.PreviewRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private final PermissionService permissionService;

    public SqlController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    /** 生成待执行 SQL 计划（insert/delete），按 数据源+表 分组。 */
    @PostMapping("/preview")
    public Map<String, Object> preview(@RequestBody PreviewRequest req) {
        return permissionService.preview(req);
    }

    /** 执行 SQL 计划：每组一个事务，失败回滚该组、继续其余组。 */
    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody ExecRequest req) {
        return permissionService.execute(req);
    }
}
