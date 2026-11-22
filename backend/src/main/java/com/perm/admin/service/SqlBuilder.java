package com.perm.admin.service;

import com.perm.admin.config.ConfigModel.FieldMapping;
import com.perm.admin.config.ConfigModel.PermTable;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 纯函数 SQL 构建器：把字段映射 + 值翻译成 {展示sql, prepared, params}。
 * 标识符（表名/列名）来自配置可信任；值一律走参数。
 */
public final class SqlBuilder {

    private SqlBuilder() {
    }

    /** 一条待执行语句 */
    public record Statement(String sql, String prepared, List<Object> params) {
    }

    /**
     * DELETE FROM t WHERE personId=? AND scope=? AND permission=? [AND personName=? | AND personName IS NULL]
     */
    public static Statement buildDelete(PermTable table, String personId, String personName,
                                        String scopeValue, String permissionValue) {
        FieldMapping f = table.fields;
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder();
        where.append(f.personId).append(" = ?");
        params.add(personId);
        where.append(" AND ").append(f.scope).append(" = ?");
        params.add(scopeValue);
        where.append(" AND ").append(f.permission).append(" = ?");
        params.add(permissionValue);
        if (f.personName != null && !f.personName.isBlank()) {
            if (personName == null || personName.isEmpty()) {
                where.append(" AND ").append(f.personName).append(" IS NULL");
            } else {
                where.append(" AND ").append(f.personName).append(" = ?");
                params.add(personName);
            }
        }
        String prepared = "DELETE FROM " + table.tableName + " WHERE " + where;
        return new Statement(interpolate(prepared, params), prepared, params);
    }

    /**
     * INSERT INTO t (映射字段..., otherFields 列...) VALUES (?, ...)
     * otherFields 支持占位符 ${personId} ${personName} ${now}；整值恰为 ${now} 时参数为当前时间戳。
     */
    public static Statement buildInsert(PermTable table, String personId, String personName,
                                        String scopeValue, String permissionValue) {
        FieldMapping f = table.fields;
        List<String> cols = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        cols.add(f.personId);
        params.add(personId);
        if (f.personName != null && !f.personName.isBlank()) {
            cols.add(f.personName);
            params.add(personName);
        }
        cols.add(f.scope);
        params.add(scopeValue);
        cols.add(f.permission);
        params.add(permissionValue);

        String now = Timestamp.from(Instant.now()).toString();
        if (table.otherFields != null) {
            for (Map.Entry<String, String> e : table.otherFields.entrySet()) {
                cols.add(e.getKey());
                params.add(evalTemplate(e.getValue(), personId, personName, now));
            }
        }

        String colSql = String.join(", ", cols);
        String marks = String.join(", ", cols.stream().map(c -> "?").toList());
        String prepared = "INSERT INTO " + table.tableName + " (" + colSql + ") VALUES (" + marks + ")";
        return new Statement(interpolate(prepared, params), prepared, params);
    }

    /**
     * 求 otherField 模板值：完全等于 "${now}" 时返回时间戳字符串参数，
     * 否则做字符串内嵌替换（${personId} ${personName} ${now}）。
     */
    private static Object evalTemplate(String template, String personId, String personName, String now) {
        if (template == null) {
            return null;
        }
        if ("${now}".equals(template.trim())) {
            return now;
        }
        String v = template;
        v = v.replace("${personId}", personId == null ? "" : personId);
        v = v.replace("${personName}", personName == null ? "" : personName);
        v = v.replace("${now}", now);
        return v;
    }

    /** 生成展示用 SQL：把 prepared 中的 ? 依次替换为转义字面量（字符串加单引号，' 转义为 ''）。 */
    public static String interpolate(String prepared, List<Object> params) {
        StringBuilder sb = new StringBuilder(prepared.length() + 32);
        int idx = 0;
        for (int i = 0; i < prepared.length(); i++) {
            char c = prepared.charAt(i);
            if (c == '?' && idx < params.size()) {
                sb.append(literal(params.get(idx++)));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String literal(Object v) {
        if (v == null) {
            return "NULL";
        }
        if (v instanceof Number) {
            return v.toString();
        }
        return "'" + v.toString().replace("'", "''") + "'";
    }
}
