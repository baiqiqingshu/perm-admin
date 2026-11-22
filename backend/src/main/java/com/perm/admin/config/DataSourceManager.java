package com.perm.admin.config;

import com.perm.admin.common.ApiException;
import com.perm.admin.config.ConfigModel.DatabaseNode;
import com.perm.admin.config.ConfigModel.EnvNode;
import com.perm.admin.config.ConfigModel.Option;
import com.perm.admin.config.ConfigModel.PermTable;
import com.perm.admin.config.ConfigModel.Project;
import com.perm.admin.service.SqlBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 database.code 懒建并缓存 HikariDataSource；
 * 启动时对 autoInit 的库建表 + 灌演示数据（仅表为空时）；
 * 第一个 autoInit 库作为 Spring 主 DataSource（供 MyBatis-Plus 等自动配置使用）。
 */
@Component
public class DataSourceManager {

    private static final Logger log = LoggerFactory.getLogger(DataSourceManager.class);

    private final ConfigService configService;
    private final Map<String, HikariDataSource> cache = new ConcurrentHashMap<>();
    private volatile DataSource primary;
    private volatile HikariDataSource fallback;

    public DataSourceManager(ConfigService configService) {
        this.configService = configService;
    }

    @PostConstruct
    public synchronized void init() {
        ConfigModel config = configService.config();
        for (Project p : config.projects) {
            for (EnvNode e : p.environments) {
                for (DatabaseNode d : e.databases) {
                    if (!d.isAutoInit()) {
                        continue;
                    }
                    try {
                        HikariDataSource ds = create(d);
                        cache.put(d.code, ds);
                        if (primary == null) {
                            primary = ds;
                        }
                        autoInitDatabase(ds, d);
                        log.info("autoInit 数据源就绪: {} ({})", d.code, d.jdbcUrl);
                    } catch (Exception ex) {
                        log.error("autoInit 数据源初始化失败: {} - {}", d.code, ex.getMessage(), ex);
                    }
                }
            }
        }
        if (primary == null) {
            // 无 autoInit 库时注册一个内存 H2 兜底，保证 MyBatis-Plus autoconfig 不报错
            log.warn("配置中没有 autoInit 数据源，使用内存 H2 作为主数据源兜底");
            HikariConfig hc = new HikariConfig();
            hc.setJdbcUrl("jdbc:h2:mem:perm-admin-fallback;DB_CLOSE_DELAY=-1");
            hc.setUsername("sa");
            hc.setPoolName("perm-fallback");
            hc.setMaximumPoolSize(2);
            fallback = new HikariDataSource(hc);
            primary = fallback;
        }
    }

    /** 配置重载后重建：关闭缓存的连接池，重新跑一遍 init。 */
    public synchronized void rebuild() {
        cache.values().forEach(HikariDataSource::close);
        cache.clear();
        primary = null;
        if (fallback != null) {
            fallback.close();
            fallback = null;
        }
        init();
    }

    /** 懒取数据源：未命中缓存时按配置创建。找不到 dbCode 抛 400。 */
    public DataSource get(String dbCode) {
        HikariDataSource ds = cache.get(dbCode);
        if (ds != null) {
            return ds;
        }
        DatabaseNode d = configService.findDatabase(dbCode)
                .orElseThrow(() -> ApiException.badRequest("数据源不存在: " + dbCode));
        return cache.computeIfAbsent(dbCode, k -> create(d));
    }

    public DataSource primary() {
        return primary;
    }

    private HikariDataSource create(DatabaseNode d) {
        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(d.jdbcUrl);
        if (d.username != null) {
            hc.setUsername(d.username);
        }
        if (d.password != null) {
            hc.setPassword(d.password);
        }
        if (d.driverClassName != null && !d.driverClassName.isBlank()) {
            hc.setDriverClassName(d.driverClassName);
        }
        hc.setPoolName("perm-" + d.code);
        hc.setMaximumPoolSize(4);
        return new HikariDataSource(hc);
    }

    // ---------- autoInit：建表 + 演示数据 ----------

    private void autoInitDatabase(DataSource ds, DatabaseNode db) throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            for (PermTable t : db.permissionTables) {
                st.execute(buildDdl(t));
                long count;
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + t.tableName)) {
                    rs.next();
                    count = rs.getLong(1);
                }
                if (count > 0) {
                    continue; // 只在表为空时灌演示数据
                }
                seedTable(c, t);
            }
        }
    }

    /**
     * CREATE TABLE IF NOT EXISTS：id 自增主键 + 映射字段 VARCHAR + otherFields 列
     * （占位符含 ${now} 的列用 TIMESTAMP，其余 VARCHAR(255)）。
     */
    private String buildDdl(PermTable t) {
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put(t.fields.personId, "VARCHAR(64)");
        if (t.fields.personName != null && !t.fields.personName.isBlank()) {
            cols.put(t.fields.personName, "VARCHAR(128)");
        }
        cols.put(t.fields.scope, "VARCHAR(64)");
        cols.put(t.fields.permission, "VARCHAR(64)");
        if (t.otherFields != null) {
            t.otherFields.forEach((col, tpl) ->
                    cols.putIfAbsent(col, tpl != null && tpl.contains("${now}") ? "TIMESTAMP" : "VARCHAR(255)"));
        }
        StringBuilder ddl = new StringBuilder();
        ddl.append("CREATE TABLE IF NOT EXISTS ").append(t.tableName)
           .append(" (id BIGINT AUTO_INCREMENT PRIMARY KEY");
        cols.forEach((col, type) -> ddl.append(", ").append(col).append(' ').append(type));
        ddl.append(')');
        return ddl.toString();
    }

    private void seedTable(Connection c, PermTable t) throws Exception {
        String[][] persons = {
                {"u001", "张三"},
                {"u002", "李四"},
                {"u003", "王五"},
                {"u009", null},   // personName 为空，验证 delete 的 IS NULL 条件
        };
        List<Option> perms = t.permissions;
        List<Option> scopes = t.scopes;
        int i = 0;
        for (String[] person : persons) {
            String permVal = perms.isEmpty() ? "R" : perms.get(i % perms.size()).value;
            String scopeVal = scopes.isEmpty() ? "ALL" : scopes.get(i % scopes.size()).value;
            SqlBuilder.Statement ins = SqlBuilder.buildInsert(t, person[0], person[1], scopeVal, permVal);
            try (PreparedStatement ps = c.prepareStatement(ins.prepared())) {
                List<Object> params = ins.params();
                for (int j = 0; j < params.size(); j++) {
                    ps.setObject(j + 1, params.get(j));
                }
                ps.executeUpdate();
            }
            i++;
        }
        log.info("表 {} 演示数据已初始化 ({} 行)", t.tableName, persons.length);
    }
}
