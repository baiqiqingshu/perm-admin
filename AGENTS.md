# perm-admin

本地权限管理工具：JSON 配置接入多项目权限表，跨库查询/批量增删，写操作先预览 SQL 再执行。

## 结构
- `backend/` Spring Boot 3.5.7 + MyBatis Plus + JdbcTemplate 动态多数据源（端口 8080）
- `frontend/` Vite + Vue3 + Element Plus（端口 5173，/api 代理 8080）
- `config/permissions.json` 唯一配置事实来源（demo 含 H2 autoInit 演示库）
- `docs/design.md` 配置 Schema + API 契约 + 运行方式 + 已知限制

## 构建/运行
```bash
# Maven 在 D:\WorkProject\.tools\apache-maven-3.9.16（阿里云镜像已配），JDK 需指定：
JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-26.0.1.8-hotspot" \
  "D:/WorkProject/.tools/apache-maven-3.9.16/bin/mvn" -f backend/pom.xml -DskipTests package
cd backend && java -jar target/perm-admin-1.0.0.jar   # 工作目录必须 backend/

cd frontend && npm install && npm run dev             # npm 走 .npmrc npmmirror
```

## 约定
- 不用 Lombok；不写测试（原型阶段）；前后端契约以 docs/design.md 为准。
- `database.code` 全局唯一；删除唯一性 = 用户×数据范围×权限。
