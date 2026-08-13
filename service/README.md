# Zorth Web SQL Service

数据源管理后端，基于 Spring Boot 2.7.18、Java 8 字节码、MyBatis、Flyway 和 MySQL。所有业务数据由服务端从鉴权上下文派生 `productId`，浏览器提交的用户或产品字段会被拒绝。

## 本地验证

前置条件：JDK 17、Maven、Docker，以及一个可访问的鉴权上下文 HTTP 端点。`bddf-authorization-service` 的内部上下文接口实现不在本项目范围内；本地测试用 WireMock 代替它。

```bash
cd service
mvn test
mvn verify
```

集成测试使用 Testcontainers 启动已有或可拉取的 `mysql:8.0`。Docker 29 要求较新的 Testcontainers；若 Docker 客户端协商失败，可显式运行 `mvn -Dapi.version=1.44 test`。无 Docker 时，仅带 `disabledWithoutDocker` 的容器测试会跳过。若本机 Maven 默认使用其他 JDK，可设置 `JAVA_HOME` 指向 JDK 17；构建仍以 `--release 8` 和 Animal Sniffer 校验 Java 8 API。

本地启动时复制 `application-local.example.yml` 到工作区外的安全位置并注入真实环境变量。服务 API 默认为 `/api/v1`，网关应转发 `Authorization: Bearer <Token>`，并保留/返回 `X-Request-Id`。Swagger/OpenAPI 位于 `/v3/api-docs`，生产环境是否经网关暴露由部署层决定。

常用验证：

```bash
cd ../web
pnpm format:check
pnpm lint
pnpm typecheck
pnpm test
pnpm e2e
VITE_LEGACY_PORTAL_URL=https://portal.example.invalid \
VITE_AUTH_BRIDGE_ALLOWED_ORIGINS=https://portal.example.invalid pnpm build
```

部署配置见 [docs/deployment.md](docs/deployment.md)，凭据轮换见 [docs/credential-rotation.md](docs/credential-rotation.md)。
