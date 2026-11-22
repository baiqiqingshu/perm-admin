package com.perm.admin.service;

import com.perm.admin.common.ApiException;
import com.perm.admin.config.ConfigModel.DatabaseNode;
import com.perm.admin.config.ConfigModel.FieldMapping;
import com.perm.admin.config.ConfigModel.Option;
import com.perm.admin.config.ConfigModel.PermTable;
import com.perm.admin.config.ConfigService;
import com.perm.admin.config.ConfigService.ResolvedTable;
import com.perm.admin.config.DataSourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限查询 / SQL 预览 / SQL 执行。
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** 每表最大返回行数 */
    private static final int MAX_ROWS_PER_TABLE = 500;
    /** label 过滤翻译失败哨兵：该表无此 label 且 raw value 也不匹配 -> 跳过该表 */
    private static final String SKIP_TABLE = "SKIP_TABLE";

    private final ConfigService configService;
    private final DataSourceManager dataSourceManager;

    public PermissionService(ConfigService configService, DataSourceManager dataSourceManager) {
        this.configService = configService;
        this.dataSourceManager = dataSourceManager;
    }

    // ---------- DTO ----------

    public record QueryRequest(String project, String environment, String database, String tableCode,
                               String permissionLabel, String scopeLabel, String keyword) {
    }

    public record PreviewRequest(String type, String project, String environment, String database,
                                 String tableCode, List<DeleteItem> items, List<Person> persons,
                                 List<String> permissions, List<String> scopes) {
    }

    public record DeleteItem(String project, String environment, String database, String tableCode,
                             String personId, String personName, String scopeValue, String permissionValue) {
    }

    public record Person(String id, String name) {
    }

    public record GroupDto(String key, String dbCode, String dbName, String jdbcUrl, String tableName,
                           List<SqlBuilder.Statement> statements) {
    }

    public record ExecRequest(List<ExecGroup> groups) {
    }

    public record ExecGroup(String key, String dbCode, String dbName, String jdbcUrl, String tableName,
                            List<ExecStatement> statements) {
    }

    public record ExecStatement(String sql, String prepared, List<Object> params) {
    }

    // ---------- 查询 ----------

    public Map<String, Object> query(QueryRequest req) {
        List<ResolvedTable> targets = configService.resolveTables(
                req.project(), req.environment(), req.database(), req.tableCode());
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();
        for (ResolvedTable rt : targets) {
            try {
                rows.addAll(queryTable(rt, req));
            } catch (Exception e) {
                log.warn("查询表失败 {}.{}: {}", rt.database().code, rt.table().tableName, e.getMessage());
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("tableCode", rt.table().code);
                err.put("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                errors.add(err);
            }
        }
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("rows", rows);
        resp.put("errors", errors);
        return resp;
    }

    private List<Map<String, Object>> queryTable(ResolvedTable rt, QueryRequest req) {
        PermTable t = rt.table();
        FieldMapping f = t.fields;

        // label -> 该表实际存储 value；该表无此 label 且 value 也不匹配则跳过该表
        String permFilter = translateFilter(t.permissions, req.permissionLabel());
        if (SKIP_TABLE.equals(permFilter)) {
            return List.of();
        }
        String scopeFilter = translateFilter(t.scopes, req.scopeLabel());
        if (SKIP_TABLE.equals(scopeFilter)) {
            return List.of();
        }

        StringBuilder select = new StringBuilder(f.personId);
        if (hasText(f.personName)) {
            select.append(", ").append(f.personName);
        }
        select.append(", ").append(f.scope).append(", ").append(f.permission);

        StringBuilder sql = new StringBuilder("SELECT ").append(select)
                .append(" FROM ").append(t.tableName).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (hasText(req.keyword())) {
            sql.append(" AND (").append(f.personId).append(" LIKE ?");
            params.add("%" + req.keyword().trim() + "%");
            if (hasText(f.personName)) {
                sql.append(" OR ").append(f.personName).append(" LIKE ?");
                params.add("%" + req.keyword().trim() + "%");
            }
            sql.append(')');
        }
        if (permFilter != null) {
            sql.append(" AND ").append(f.permission).append(" = ?");
            params.add(permFilter);
        }
        if (scopeFilter != null) {
            sql.append(" AND ").append(f.scope).append(" = ?");
            params.add(scopeFilter);
        }
        sql.append(" LIMIT ").append(MAX_ROWS_PER_TABLE);

        DataSource ds = dataSourceManager.get(rt.database().code);
        JdbcTemplate jt = new JdbcTemplate(ds);
        return jt.query(sql.toString(), (rs, rowNum) -> {
            String scopeValue = rs.getString(f.scope);
            String permValue = rs.getString(f.permission);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("project", rt.project().code);
            m.put("projectName", rt.project().name);
            m.put("environment", rt.environment().code);
            m.put("environmentName", rt.environment().name);
            m.put("database", rt.database().code);
            m.put("databaseName", rt.database().name);
            m.put("tableCode", t.code);
            m.put("tableName", t.tableName);
            m.put("personId", rs.getString(f.personId));
            m.put("personName", hasText(f.personName) ? rs.getString(f.personName) : null);
            m.put("scopeValue", scopeValue);
            m.put("scopeLabel", labelOf(t.scopes, scopeValue));
            m.put("permissionValue", permValue);
            m.put("permissionLabel", labelOf(t.permissions, permValue));
            return m;
        }, params.toArray());
    }

    /**
     * 过滤值翻译：null/空白 -> 不过滤(返回 null)；匹配到 label 或 value -> 返回实际 value；
     * 否则返回 SKIP_TABLE 跳过该表。
     */
    private String translateFilter(List<Option> options, String filter) {
        if (!hasText(filter)) {
            return null;
        }
        for (Option o : options) {
            if (filter.equals(o.label)) {
                return o.value;
            }
        }
        for (Option o : options) {
            if (filter.equals(o.value)) {
                return o.value;
            }
        }
        return SKIP_TABLE;
    }

    private String labelOf(List<Option> options, String value) {
        if (value == null) {
            return null;
        }
        for (Option o : options) {
            if (value.equals(o.value)) {
                return o.label;
            }
        }
        return value;
    }

    // ---------- 预览 ----------

    public Map<String, Object> preview(PreviewRequest req) {
        if (req == null || !hasText(req.type())) {
            throw ApiException.badRequest("type 必填（insert 或 delete）");
        }
        return switch (req.type()) {
            case "delete" -> previewDelete(req);
            case "insert" -> previewInsert(req);
            default -> throw ApiException.badRequest("不支持的预览类型: " + req.type());
        };
    }

    private Map<String, Object> previewDelete(PreviewRequest req) {
        if (req.items() == null || req.items().isEmpty()) {
            throw ApiException.badRequest("删除预览 items 不能为空");
        }
        Map<String, GroupAcc> groups = new LinkedHashMap<>();
        for (DeleteItem item : req.items()) {
            ResolvedTable rt = resolveUnique(item.project(), item.environment(),
                    item.database(), item.tableCode());
            SqlBuilder.Statement st = SqlBuilder.buildDelete(rt.table(), item.personId(),
                    item.personName(), item.scopeValue(), item.permissionValue());
            acc(groups, rt).statements.add(st);
        }
        return Map.of("groups", toGroupDtos(groups));
    }

    private Map<String, Object> previewInsert(PreviewRequest req) {
        ResolvedTable rt = resolveUnique(req.project(), req.environment(), req.database(), req.tableCode());
        if (req.persons() == null || req.persons().isEmpty()) {
            throw ApiException.badRequest("新增预览 persons 不能为空");
        }
        if (req.permissions() == null || req.permissions().isEmpty()) {
            throw ApiException.badRequest("新增预览 permissions 不能为空");
        }
        if (req.scopes() == null || req.scopes().isEmpty()) {
            throw ApiException.badRequest("新增预览 scopes 不能为空");
        }
        Map<String, GroupAcc> groups = new LinkedHashMap<>();
        GroupAcc acc = acc(groups, rt);
        for (Person person : req.persons()) {
            for (String perm : req.permissions()) {
                for (String scope : req.scopes()) {
                    acc.statements.add(SqlBuilder.buildInsert(rt.table(), person.id(),
                            person.name(), scope, perm));
                }
            }
        }
        return Map.of("groups", toGroupDtos(groups));
    }

    private ResolvedTable resolveUnique(String project, String env, String db, String tableCode) {
        List<ResolvedTable> list = configService.resolveTables(project, env, db, tableCode);
        if (list.isEmpty()) {
            throw ApiException.badRequest("未找到匹配的权限表");
        }
        if (list.size() > 1) {
            throw ApiException.badRequest("权限表定位不唯一（匹配到 " + list.size()
                    + " 张表），请补全项目/环境/数据库/表编码");
        }
        return list.get(0);
    }

    private GroupAcc acc(Map<String, GroupAcc> groups, ResolvedTable rt) {
        DatabaseNode d = rt.database();
        PermTable t = rt.table();
        String key = d.code + "|" + t.tableName;
        return groups.computeIfAbsent(key, k -> new GroupAcc(k, d.code, d.name, d.jdbcUrl, t.tableName));
    }

    private List<GroupDto> toGroupDtos(Map<String, GroupAcc> groups) {
        return groups.values().stream()
                .map(g -> new GroupDto(g.key, g.dbCode, g.dbName, g.jdbcUrl, g.tableName, g.statements))
                .toList();
    }

    private static class GroupAcc {
        final String key;
        final String dbCode;
        final String dbName;
        final String jdbcUrl;
        final String tableName;
        final List<SqlBuilder.Statement> statements = new ArrayList<>();

        GroupAcc(String key, String dbCode, String dbName, String jdbcUrl, String tableName) {
            this.key = key;
            this.dbCode = dbCode;
            this.dbName = dbName;
            this.jdbcUrl = jdbcUrl;
            this.tableName = tableName;
        }
    }

    // ---------- 执行 ----------

    public Map<String, Object> execute(ExecRequest req) {
        if (req == null || req.groups() == null || req.groups().isEmpty()) {
            throw ApiException.badRequest("执行请求 groups 不能为空");
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (ExecGroup g : req.groups()) {
            String key = hasText(g.key()) ? g.key() : g.dbCode() + "|" + g.tableName();
            DataSource ds;
            try {
                ds = dataSourceManager.get(g.dbCode());
            } catch (Exception e) {
                results.add(result(key, false, 0, "数据源获取失败: " + msg(e)));
                continue;
            }
            results.add(runGroup(ds, key, g));
        }
        return Map.of("results", results);
    }

    /** 每组一个事务：全成功 commit，任一失败 rollback 并记录 error，继续处理后续组。 */
    private Map<String, Object> runGroup(DataSource ds, String key, ExecGroup g) {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            int rows = 0;
            try {
                if (g.statements() != null) {
                    for (ExecStatement s : g.statements()) {
                        try (PreparedStatement ps = c.prepareStatement(s.prepared())) {
                            List<Object> params = s.params() == null ? List.of() : s.params();
                            for (int i = 0; i < params.size(); i++) {
                                ps.setObject(i + 1, params.get(i));
                            }
                            rows += ps.executeUpdate();
                        }
                    }
                }
                c.commit();
                return result(key, true, rows, null);
            } catch (Exception e) {
                try {
                    c.rollback();
                } catch (Exception rollbackEx) {
                    log.warn("回滚失败 {}: {}", key, rollbackEx.getMessage());
                }
                return result(key, false, rows, msg(e));
            }
        } catch (Exception e) {
            return result(key, false, 0, msg(e));
        }
    }

    private Map<String, Object> result(String key, boolean success, int rows, String error) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("key", key);
        m.put("success", success);
        m.put("rows", rows);
        m.put("error", error);
        return m;
    }

    private static String msg(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static boolean hasText(String s) {
        return ConfigService.hasText(s);
    }
}
