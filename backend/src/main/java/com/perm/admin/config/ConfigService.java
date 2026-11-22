package com.perm.admin.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.perm.admin.common.ApiException;
import com.perm.admin.config.ConfigModel.DatabaseNode;
import com.perm.admin.config.ConfigModel.EnvNode;
import com.perm.admin.config.ConfigModel.PermTable;
import com.perm.admin.config.ConfigModel.Project;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 加载/重载权限配置，并提供按 code 解析树的工具方法。
 * 路径规则：环境变量 PERM_CONFIG 优先，否则 ../config/permissions.json（相对工作目录）。
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final ObjectMapper mapper = new ObjectMapper();

    private volatile ConfigModel config = new ConfigModel();
    private volatile Path configPath;

    /** 解析后的权限表 + 其所属上下文 */
    public record ResolvedTable(Project project, EnvNode environment, DatabaseNode database, PermTable table) {
    }

    @PostConstruct
    public void init() {
        load();
    }

    public synchronized void load() {
        String env = System.getenv("PERM_CONFIG");
        Path p = (env != null && !env.isBlank())
                ? Path.of(env).toAbsolutePath().normalize()
                : Path.of("..", "config", "permissions.json").toAbsolutePath().normalize();
        try {
            this.config = mapper.readValue(p.toFile(), ConfigModel.class);
            this.configPath = p;
            log.info("权限配置加载成功: {}", p);
        } catch (IOException e) {
            log.error("权限配置加载失败: {} - {}", p, e.getMessage());
            throw new ApiException(500, "权限配置加载失败: " + p + " - " + e.getMessage());
        }
    }

    public ConfigModel config() {
        return config;
    }

    public Path configPath() {
        return configPath;
    }

    /**
     * 返回脱敏配置树（所有 password 字段替换为 ***）。
     */
    public JsonNode maskedConfig() {
        JsonNode tree = mapper.valueToTree(config);
        maskPasswords(tree);
        return tree;
    }

    private void maskPasswords(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            if (obj.has("password")) {
                obj.put("password", "***");
            }
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                maskPasswords(it.next().getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                maskPasswords(child);
            }
        }
    }

    /**
     * 按可选的 项目/环境/数据库/表 code 过滤配置树；显式指定但解析不到时抛 400。
     */
    public List<ResolvedTable> resolveTables(String projectCode, String envCode, String dbCode, String tableCode) {
        List<Project> projects = new ArrayList<>(config.projects);
        if (hasText(projectCode)) {
            projects.removeIf(p -> !projectCode.equals(p.code));
            if (projects.isEmpty()) {
                throw ApiException.badRequest("项目不存在: " + projectCode);
            }
        }
        List<ResolvedTable> out = new ArrayList<>();
        boolean envFound = false;
        boolean dbFound = false;
        boolean tableFound = false;
        for (Project p : projects) {
            for (EnvNode e : p.environments) {
                if (hasText(envCode) && !envCode.equals(e.code)) {
                    continue;
                }
                envFound = true;
                for (DatabaseNode d : e.databases) {
                    if (hasText(dbCode) && !dbCode.equals(d.code)) {
                        continue;
                    }
                    dbFound = true;
                    for (PermTable t : d.permissionTables) {
                        if (hasText(tableCode) && !tableCode.equals(t.code)) {
                            continue;
                        }
                        tableFound = true;
                        out.add(new ResolvedTable(p, e, d, t));
                    }
                }
            }
        }
        if (hasText(envCode) && !envFound) {
            throw ApiException.badRequest("环境不存在: " + envCode);
        }
        if (hasText(dbCode) && !dbFound) {
            throw ApiException.badRequest("数据库不存在: " + dbCode);
        }
        if (hasText(tableCode) && !tableFound) {
            throw ApiException.badRequest("权限表不存在: " + tableCode);
        }
        return out;
    }

    /**
     * 在整棵树里按 database.code 找数据源配置（execute 按 dbCode 定位连接）。
     */
    public Optional<DatabaseNode> findDatabase(String dbCode) {
        if (!hasText(dbCode)) {
            return Optional.empty();
        }
        for (Project p : config.projects) {
            for (EnvNode e : p.environments) {
                for (DatabaseNode d : e.databases) {
                    if (dbCode.equals(d.code)) {
                        return Optional.of(d);
                    }
                }
            }
        }
        return Optional.empty();
    }

    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
