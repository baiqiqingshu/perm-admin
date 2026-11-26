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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 权限查询 / SQL 预览 / SQL 执行。
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

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
                               String permissionLabel, String scopeLabel, String keyword,
                               List<String> projects, List<String> environments,
                               List<String> databases, List<String> tableCodes,
                               List<String> permissionLabels, List<String> scopeLabels) {
    }

    public record PreviewRequest(String type, String project, String environment, String database,
                                 String tableCode, Targets targets,
                                 List<DeleteItem> items, List<Person> persons,
                                 List<String> permissions, List<String> scopes,
                                 List<String> permissionLabels, List<String> scopeLabels) {
    }

    /** insert 新契约的目标过滤：各级均为可选多选，空/缺省 = 该级不筛选。 */
    public record Targets(List<String> projects, List<String> environments,
                          List<String> databases, List<String> tableCodes) {
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
        List<ResolvedTable> targets = resolveQueryTargets(req);
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
        List<String> permFilters = translateFilters(t.permissions,
                singleOrMulti(req.permissionLabel(), req.permissionLabels()));
        if (permFilters != null && permFilters.isEmpty()) {
            return List.of();
        }
        List<String> scopeFilters = translateFilters(t.scopes,
                singleOrMulti(req.scopeLabel(), req.scopeLabels()));
        if (scopeFilters != null && scopeFilters.isEmpty()) {
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
        if (permFilters != null) {
            sql.append(" AND ").append(f.permission).append(" IN (")
                    .append(String.join(", ", permFilters.stream().map(v -> "?").toList()))
                    .append(")");
            params.addAll(permFilters);
        }
        if (scopeFilters != null) {
            sql.append(" AND ").append(f.scope).append(" IN (")
                    .append(String.join(", ", scopeFilters.stream().map(v -> "?").toList()))
                    .append(")");
            params.addAll(scopeFilters);
        }

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

    private List<ResolvedTable> resolveQueryTargets(QueryRequest req) {
        return configService.resolveTablesMulti(
                singleOrMulti(req.project(), req.projects()),
                singleOrMulti(req.environment(), req.environments()),
                singleOrMulti(req.database(), req.databases()),
                singleOrMulti(req.tableCode(), req.tableCodes()));
    }

    private List<String> singleOrMulti(String single, List<String> multi) {
        if (hasText(single)) {
            return List.of(single.trim());
        }
        return normalizeFilterValues(multi);
    }

    private List<String> normalizeFilterValues(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (hasText(value)) {
                normalized.add(value.trim());
            }
        }
        return normalized.isEmpty() ? null : new ArrayList<>(normalized);
    }

    private List<String> translateFilters(List<Option> options, List<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }
        Set<String> translatedValues = new LinkedHashSet<>();
        for (String filter : filters) {
            String translated = translateFilter(options, filter);
            if (translated != null && !SKIP_TABLE.equals(translated)) {
                translatedValues.add(translated);
            }
        }
        return new ArrayList<>(translatedValues);
    }

    /**
     * 过滤值翻译：null/空白 -> 不过滤(返回 null)；匹配到 label 或 value -> 返回实际 value；
     * 否则返回 SKIP_TABLE 跳过该表。
     */
    private String translateFilter(List<Option> options, String filter) {
        if (!hasText(filter)) {
            return null;
        }
        if (options == null) {
            return SKIP_TABLE;
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

    /**
     * insert 预览（iter2 新契约 + 旧契约兼容）：
     * - 目标表 = targets 各级多选过滤（缺省/空 = 不筛选）命中的全部 (database, table)；
     *   旧字段 project/environment/database/tableCode 存在时视为该级单元素 targets 并优先。
     * - 权限/范围：旧字段 permissions/scopes（裸 value）存在时优先直接用 value；
     *   否则 permissionLabels/scopeLabels 按各表配置翻译 value，表内无该 label -> 记入 unsupported。
     * - 去重：每表先查 personF IN (...) 的已有 (personId, scopeValue, permissionValue) 三元组，
     *   已存在 -> 记入 skipped 不生成 INSERT；查询失败记 errors 并照常生成（不整体失败）。
     * - persons 为空/无目标表/全部 label 不受支持 -> 200 + 空 groups（persons 字段缺失才 400）。
     */
    private Map<String, Object> previewInsert(PreviewRequest req) {
        if (req.persons() == null) {
            throw ApiException.badRequest("新增预览 persons 不能为空");
        }
        // 旧字段（裸 value）存在时优先；否则用 label 列表
        boolean rawPerm = req.permissions() != null;
        boolean rawScope = req.scopes() != null;
        List<String> permReqs = rawPerm ? req.permissions() : req.permissionLabels();
        List<String> scopeReqs = rawScope ? req.scopes() : req.scopeLabels();
        if (permReqs == null) {
            throw ApiException.badRequest("新增预览 permissions 或 permissionLabels 必填");
        }
        if (scopeReqs == null) {
            throw ApiException.badRequest("新增预览 scopes 或 scopeLabels 必填");
        }

        List<ResolvedTable> targets = resolveTargets(req);
        List<String> personIds = req.persons().stream()
                .map(Person::id).filter(Objects::nonNull).distinct().toList();

        Map<String, GroupAcc> groups = new LinkedHashMap<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        List<Map<String, Object>> unsupported = new ArrayList<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (ResolvedTable rt : targets) {
            PermTable t = rt.table();
            String dbCode = rt.database().code;
            String tableName = t.tableName;

            List<Sel> perms = resolveOptions(t.permissions, permReqs, rawPerm,
                    dbCode, tableName, "permission", unsupported);
            List<Sel> scopes = resolveOptions(t.scopes, scopeReqs, rawScope,
                    dbCode, tableName, "scope", unsupported);
            if (perms.isEmpty() || scopes.isEmpty() || req.persons().isEmpty()) {
                continue;
            }

            Set<List<String>> existing;
            try {
                existing = loadExisting(rt, personIds);
            } catch (Exception e) {
                log.warn("去重查询失败 {}.{}: {}", dbCode, tableName, e.getMessage());
                errors.add(entry("dbCode", dbCode, "tableName", tableName, "error", msg(e)));
                existing = Set.of();
            }

            GroupAcc acc = null;
            for (Person person : req.persons()) {
                for (Sel perm : perms) {
                    for (Sel scope : scopes) {
                        if (existing.contains(Arrays.asList(person.id(), scope.value(), perm.value()))) {
                            skipped.add(entry("dbCode", dbCode, "tableName", tableName,
                                    "personId", person.id(),
                                    "permissionLabel", perm.display(), "scopeLabel", scope.display()));
                            continue;
                        }
                        if (acc == null) {
                            acc = acc(groups, rt);
                        }
                        acc.statements.add(SqlBuilder.buildInsert(t, person.id(),
                                person.name(), scope.value(), perm.value()));
                    }
                }
            }
        }

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("groups", toGroupDtos(groups));
        resp.put("skipped", skipped);
        resp.put("unsupported", unsupported);
        resp.put("errors", errors);
        return resp;
    }

    /** 已选中的 权限/范围 项：display 用于 skipped 回填（label 或裸 value），value 为实际存储值。 */
    private record Sel(String display, String value) {
    }

    /**
     * 把请求的 label（或裸 value）列表按某张表的配置翻译为实际 value 列表；
     * label 模式下表内无该 label -> 记 unsupported 并跳过该 label。
     */
    private List<Sel> resolveOptions(List<Option> options, List<String> requested, boolean raw,
                                     String dbCode, String tableName, String type,
                                     List<Map<String, Object>> unsupported) {
        List<Sel> out = new ArrayList<>();
        for (String req : requested) {
            if (raw) {
                out.add(new Sel(req, req));
                continue;
            }
            String value = valueOfLabel(options, req);
            if (value == null) {
                unsupported.add(entry("dbCode", dbCode, "tableName", tableName,
                        "type", type, "label", req));
            } else {
                out.add(new Sel(req, value));
            }
        }
        return out;
    }

    /** label -> value（仅匹配 label，不匹配 raw value）；无此 label 返回 null。 */
    private String valueOfLabel(List<Option> options, String label) {
        if (label == null) {
            return null;
        }
        for (Option o : options) {
            if (label.equals(o.label)) {
                return o.value;
            }
        }
        return null;
    }

    /** 合并 targets 与旧单值字段：旧字段存在时优先作为该级单元素列表。 */
    private List<ResolvedTable> resolveTargets(PreviewRequest req) {
        Targets t = req.targets();
        List<String> projects = hasText(req.project()) ? List.of(req.project())
                : (t != null ? t.projects() : null);
        List<String> envs = hasText(req.environment()) ? List.of(req.environment())
                : (t != null ? t.environments() : null);
        List<String> dbs = hasText(req.database()) ? List.of(req.database())
                : (t != null ? t.databases() : null);
        List<String> tables = hasText(req.tableCode()) ? List.of(req.tableCode())
                : (t != null ? t.tableCodes() : null);
        return configService.resolveTablesMulti(projects, envs, dbs, tables);
    }

    /**
     * 查某表已有 (personId, scopeValue, permissionValue) 三元组：
     * SELECT personF, scopeF, permF FROM t WHERE personF IN (?, ...)。
     */
    private Set<List<String>> loadExisting(ResolvedTable rt, List<String> personIds) {
        Set<List<String>> existing = new HashSet<>();
        if (personIds.isEmpty()) {
            return existing;
        }
        PermTable t = rt.table();
        FieldMapping f = t.fields;
        String marks = String.join(", ", personIds.stream().map(id -> "?").toList());
        String sql = "SELECT " + f.personId + ", " + f.scope + ", " + f.permission
                + " FROM " + t.tableName + " WHERE " + f.personId + " IN (" + marks + ")";
        DataSource ds = dataSourceManager.get(rt.database().code);
        JdbcTemplate jt = new JdbcTemplate(ds);
        List<List<String>> rows = jt.query(sql,
                (rs, rowNum) -> Arrays.asList(rs.getString(1), rs.getString(2), rs.getString(3)),
                personIds.toArray());
        existing.addAll(rows);
        return existing;
    }

    /** 构造有序 map 响应条目（成对的 key/value 参数）。 */
    private static Map<String, Object> entry(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
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
