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
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 加载/重载权限配置，并提供按 code 解析树的工具方法。
 * 路径解析顺序（打包成 exe 后，"exe 同目录的 permissions.json"即为默认配置位置）：
 * PERM_CONFIG 环境变量 > perm.config 属性(-D/--perm.config) >
 * ./permissions.json > ./config/permissions.json > ../config/permissions.json >
 * jpackage.app-path 旁 permissions.json > classpath:/permissions.json（jar 内置默认配置兜底）。
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final String CLASSPATH_DEFAULT = "/permissions.json";

    private final ObjectMapper mapper = new ObjectMapper();
    private final Environment environment;

    private volatile ConfigModel config = new ConfigModel();
    private volatile Path configPath;

    public ConfigService(Environment environment) {
        this.environment = environment;
    }

    /** 解析后的权限表 + 其所属上下文 */
    public record ResolvedTable(Project project, EnvNode environment, DatabaseNode database, PermTable table) {
    }

    @PostConstruct
    public void init() {
        load();
    }

    public synchronized void load() {
        Path p = resolveConfigPath();
        try {
            if (p != null) {
                this.config = mapper.readValue(p.toFile(), ConfigModel.class);
                this.configPath = p;
                log.info("权限配置加载成功: {}", p);
            } else {
                try (InputStream in = ConfigService.class.getResourceAsStream(CLASSPATH_DEFAULT)) {
                    if (in == null) {
                        throw new ApiException(500, "权限配置加载失败: 未找到 permissions.json");
                    }
                    this.config = mapper.readValue(in, ConfigModel.class);
                    this.configPath = null;
                    log.info("使用 jar 内置默认权限配置 classpath:{}", CLASSPATH_DEFAULT);
                }
            }
        } catch (IOException e) {
            String src = p != null ? p.toString() : "classpath:" + CLASSPATH_DEFAULT;
            log.error("权限配置加载失败: {} - {}", src, e.getMessage());
            throw new ApiException(500, "权限配置加载失败: " + src + " - " + e.getMessage());
        }
    }

    /**
     * 解析外置配置路径；显式指定（env/属性）的路径即使不存在也返回（读文件时报错），
     * 隐式候选只返回实际存在的文件；全部不命中返回 null（走 classpath 兜底）。
     */
    private Path resolveConfigPath() {
        String env = System.getenv("PERM_CONFIG");
        if (hasText(env)) {
            return Path.of(env).toAbsolutePath().normalize();
        }
        String prop = environment.getProperty("perm.config");
        if (hasText(prop)) {
            return Path.of(prop).toAbsolutePath().normalize();
        }
        for (String candidate : new String[]{"permissions.json", "config/permissions.json", "../config/permissions.json"}) {
            Path p = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        // jpackage 启动器写入 jpackage.app-path=exe 全路径；自解压壳会改工作目录，
        // 兜底用 exe 所在目录找配置（EVB 壳内为虚拟路径，不影响外置文件读取顺序）
        String appPath = System.getProperty("jpackage.app-path");
        if (hasText(appPath)) {
            Path p = Path.of(appPath).toAbsolutePath().normalize().getParent().resolve("permissions.json");
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        return null;
    }

    public ConfigModel config() {
        return config;
    }

    /** 外置配置文件路径；使用 jar 内置默认配置时为 null。 */
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
     * 多值版本的目标表解析（insert 新契约 targets）：每级 code 列表为空/缺省表示该级不筛选；
     * 不抛异常，全部不匹配时返回空列表（由调用方决定返回空结果）。
     */
    public List<ResolvedTable> resolveTablesMulti(List<String> projectCodes, List<String> envCodes,
                                                  List<String> dbCodes, List<String> tableCodes) {
        List<ResolvedTable> out = new ArrayList<>();
        for (Project p : config.projects) {
            if (!matchAny(projectCodes, p.code)) {
                continue;
            }
            for (EnvNode e : p.environments) {
                if (!matchAny(envCodes, e.code)) {
                    continue;
                }
                for (DatabaseNode d : e.databases) {
                    if (!matchAny(dbCodes, d.code)) {
                        continue;
                    }
                    for (PermTable t : d.permissionTables) {
                        if (!matchAny(tableCodes, t.code)) {
                            continue;
                        }
                        out.add(new ResolvedTable(p, e, d, t));
                    }
                }
            }
        }
        return out;
    }

    /** codes 为 null/空 -> 不筛选；否则 code 需命中其中之一。 */
    private static boolean matchAny(List<String> codes, String code) {
        return codes == null || codes.isEmpty() || codes.contains(code);
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
