package com.perm.admin.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 权限配置模型，对应 config/permissions.json。
 * 结构：项目 -> 环境 -> 数据库 -> 权限表 四级树。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ConfigModel {

    public List<Project> projects = List.of();

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Project {
        public String code;
        public String name;
        public List<EnvNode> environments = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EnvNode {
        public String code;
        public String name;
        public List<DatabaseNode> databases = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DatabaseNode {
        public String code;
        public String name;
        public String jdbcUrl;
        public String username;
        public String password;
        public String driverClassName;
        public Boolean autoInit;
        public List<PermTable> permissionTables = List.of();

        public boolean isAutoInit() {
            return Boolean.TRUE.equals(autoInit);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PermTable {
        public String code;
        public String name;
        public String tableName;
        public FieldMapping fields = new FieldMapping();
        /** LinkedHashMap 保持配置中的列顺序 */
        public Map<String, String> otherFields = new LinkedHashMap<>();
        public List<Option> scopes = List.of();
        public List<Option> permissions = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FieldMapping {
        public String personId;
        public String personName;
        public String scope;
        public String permission;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Option {
        public String value;
        public String label;

        public Option() {
        }

        public Option(String value, String label) {
            this.value = value;
            this.label = label;
        }
    }
}
