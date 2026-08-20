# 第二阶段本地完整链路

本地链路为 `Vue (5173) → auth-service (8090) / SQL service (8080) → MySQL`。临时授权服务只用于开发；生产必须配置真实 `bddf-authorization-service`。

## 1. 准备 MySQL

创建一个供 SQL service 保存配置/历史的 `sql_editor` 数据库，并准备一个可作为编辑器目标的数据源。两者可以是同一 MySQL 实例。元数据库用户需要 `sql_editor` 的 DDL/DML 权限；目标账号权限决定编辑器能执行的 SQL。

## 2. 启动临时授权服务

```bash
cd auth-service
AUTH_SERVICE_INTERNAL_KEY=local-sql-editor-key node server.js
```

## 3. 启动 SQL service

以下密钥仅是本地示例。目标库位于本机时，需要显式允许 loopback，并从 denied CIDR 中移除 loopback；生产不可照抄。

```bash
cd service
SQL_EDITOR_METADATA_URL='jdbc:mysql://127.0.0.1:3306/sql_editor?serverTimezone=UTC' \
SQL_EDITOR_METADATA_USERNAME=root \
SQL_EDITOR_METADATA_PASSWORD='<mysql-password>' \
SQL_EDITOR_AUTH_CONTEXT_URL='http://127.0.0.1:8090/internal/api/v1/auth/context' \
SQL_EDITOR_AUTH_INTERNAL_SERVICE_KEY='local-sql-editor-key' \
SQL_EDITOR_CREDENTIAL_CURRENT_KEY='MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=' \
SQL_EDITOR_CURSOR_SIGNING_KEY='YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODk=' \
SQL_EDITOR_NETWORK_ALLOWED_CIDRS='127.0.0.0/8' \
SQL_EDITOR_NETWORK_DENIED_CIDRS='169.254.0.0/16,::1/128,fe80::/10' \
mvn spring-boot:run
```

## 4. 启动 Web

```bash
cd web
VITE_SQL_API_BASE='/sql-api' \
VITE_AUTH_API_BASE='/auth-api' \
VITE_DEV_SQL_PROXY_TARGET='http://127.0.0.1:8080' \
VITE_DEV_AUTH_PROXY_TARGET='http://127.0.0.1:8090' \
VITE_LEGACY_PORTAL_URL='http://localhost:8080/account/bind' \
VITE_AUTH_BRIDGE_ALLOWED_ORIGINS='http://localhost:8080' \
VITE_ENABLE_API_MOCK=false \
pnpm dev
```

打开 `http://localhost:5173/login`，输入任意非空用户名和密码。登录后进入 SQL 编辑器；先在“数据源管理”中新增目标 MySQL，再回到编辑器选择数据源和数据库。

若要连接真实 GBase 8a，先按 `service/third-party/gbase/README.md` 放入官方 `gbase-connector-java.jar`，再重新启动 SQL service。未放入时 MYSQL / PostgreSQL 不受影响，GBase 8a 测试连接会失败。

Vite 开发代理会把 `/sql-api` 和 `/auth-api` 分别转发到两个本地服务，从而保持浏览器同源。生产不得配置 `VITE_DEV_*_PROXY_TARGET`，应由真实网关提供同源路径。
