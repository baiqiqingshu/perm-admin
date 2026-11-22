package com.perm.admin.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.perm.admin.config.ConfigService;
import com.perm.admin.config.DataSourceManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ConfigService configService;
    private final DataSourceManager dataSourceManager;

    public ConfigController(ConfigService configService, DataSourceManager dataSourceManager) {
        this.configService = configService;
        this.dataSourceManager = dataSourceManager;
    }

    /** 配置树（密码已脱敏为 ***）。 */
    @GetMapping
    public JsonNode get() {
        return configService.maskedConfig();
    }

    /** 从磁盘重读配置，并重建数据源缓存；返回新的脱敏配置树。 */
    @PostMapping("/reload")
    public JsonNode reload() {
        configService.load();
        dataSourceManager.rebuild();
        return configService.maskedConfig();
    }
}
