package com.bocsoft.sqleditor;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresEngineIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("sql_editor_test").withUsername("editor").withPassword("editor-password");
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("orders").withUsername("editor").withPassword("editor-password");
    static final WireMockServer AUTH = new WireMockServer(0);
    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    static {
        String extra = "localhost|127.*|[::1]";
        String current = System.getProperty("socksNonProxyHosts");
        System.setProperty("socksNonProxyHosts", current == null || current.isEmpty() ? extra : extra + "|" + current);
        System.clearProperty("socksProxyHost");
        System.clearProperty("socksProxyPort");
        AUTH.start();
    }

    @BeforeAll
    static void setup() throws Exception {
        configureFor("localhost", AUTH.port());
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context"))
            .withHeader("Authorization", equalTo("Bearer token-a"))
            .willReturn(okJson(context("user-a", "product-a", "产品 A"))));
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context"))
            .atPriority(10)
            .willReturn(unauthorized().withHeader("Content-Type", "application/json").withBody("{\"code\":\"UNAUTHENTICATED\"}")));
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS sales");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.order_item (id int primary key, name text)");
            statement.execute("INSERT INTO sales.order_item (id, name) VALUES (1, 'a') ON CONFLICT DO NOTHING");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.customer (id int primary key, email text not null, code text not null, CONSTRAINT uk_rel_email UNIQUE (email))");
            statement.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_rel_code ON sales.customer (code)");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.parent_comp (a int not null, b int not null, primary key (a,b))");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.child_rel (id int primary key, customer_id int references sales.customer(id), a int, b int)");
            statement.execute("ALTER TABLE sales.child_rel DROP CONSTRAINT IF EXISTS fk_comp");
            statement.execute("ALTER TABLE sales.child_rel ADD CONSTRAINT fk_comp FOREIGN KEY (a,b) REFERENCES sales.parent_comp(a,b)");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.lookalike (id int primary key, customer_id int)");
            statement.execute("CREATE INDEX IF NOT EXISTS idx_lookalike_customer ON sales.lookalike (customer_id)");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.ref1 (id int primary key, customer_id int references sales.customer(id))");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.ref2 (id int primary key, customer_id int references sales.customer(id))");
            statement.execute("CREATE TABLE IF NOT EXISTS sales.ref3 (id int primary key, customer_id int references sales.customer(id))");
        }
    }

    @AfterAll static void stopAuth() { AUTH.stop(); }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("sql-editor.auth.context-url", () -> AUTH.baseUrl() + "/internal/api/v1/auth/context");
        registry.add("sql-editor.auth.internal-service-key", () -> "integration-service-key");
        registry.add("sql-editor.auth.cache-ttl-seconds", () -> 0);
        registry.add("sql-editor.credentials.current-version", () -> "v1");
        registry.add("sql-editor.credentials.keys.v1", () -> KEY);
        registry.add("sql-editor.cursor.signing-key", () -> KEY);
        registry.add("sql-editor.network.allowed-cidrs[0]", () -> "127.0.0.0/8");
        registry.add("sql-editor.network.allowed-cidrs[1]", () -> "::1/128");
        registry.add("sql-editor.network.denied-cidrs[0]", () -> "192.0.2.0/24");
        registry.add("management.server.port", () -> "-1");
    }

    @Test void postgresqlIsSecondRelationalEngineWithSchemaAsNamespace() throws Exception {
        mvc.perform(get("/api/v1/engines").header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].id").value("MYSQL"))
            .andExpect(jsonPath("$.items[1].id").value("POSTGRESQL"))
            .andExpect(jsonPath("$.items[2].id").value("GBASE_8A"))
            .andExpect(jsonPath("$.items[1].resourceTree[0].label").value("模式"));

        mvc.perform(post("/api/v1/data-sources").header("Authorization", "Bearer token-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload(POSTGRES.getPassword()).replace("\"defaultDatabase\":\"orders\",", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.details.fieldErrors[0].field").value("defaultDatabase"));

        JsonNode created = json.readTree(mvc.perform(post("/api/v1/data-sources").header("Authorization", "Bearer token-a")
                .contentType(MediaType.APPLICATION_JSON).content(payload(POSTGRES.getPassword())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        String id = created.path("id").asText();
        assertThat(created.path("engine").asText()).isEqualTo("POSTGRESQL");
        assertThat(created.path("defaultDatabase").asText()).isEqualTo("orders");

        mvc.perform(post("/api/v1/data-sources:test").header("Authorization", "Bearer token-a")
                .contentType(MediaType.APPLICATION_JSON).content(testPayload(POSTGRES.getPassword())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(post("/api/v1/data-sources:test").header("Authorization", "Bearer token-a")
                .contentType(MediaType.APPLICATION_JSON).content(testPayload("wrong-password")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.failureCode").value("AUTHENTICATION_FAILED"));
        mvc.perform(post("/api/v1/data-sources:test").header("Authorization", "Bearer token-a")
                .contentType(MediaType.APPLICATION_JSON)
                .content(testPayload(POSTGRES.getPassword()).replace("\"defaultDatabase\":\"orders\"", "\"defaultDatabase\":\"missing_database\"")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.failureCode").value("DATABASE_NOT_FOUND"));

        MvcResult databases = mvc.perform(get("/api/v1/data-sources/" + id + "/databases").header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].kind").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("NAMESPACE"))))
            .andReturn();
        String listed = databases.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(listed).contains("\"name\":\"public\"").contains("\"name\":\"sales\"");
        assertThat(listed).doesNotContain("pg_catalog").doesNotContain("information_schema");

        mvc.perform(get("/api/v1/data-sources/" + id + "/tables").param("database", "sales").header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.hasItem("order_item")))
            .andExpect(jsonPath("$.items[*].database").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("sales"))));

        executeSelect(id, "sales", "SELECT * FROM order_item");
        executeSelect(id, "public", "SELECT $tag$ hello; world $tag$ AS v");

        mvc.perform(get("/internal/api/v1/agent/data-sources/" + id + "/columns")
                .param("database", "sales").param("keyword", "name").header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].table").value("order_item"))
            .andExpect(jsonPath("$.items[0].jdbcType").isString());
        mvc.perform(get("/api/v1/data-sources/" + id + "/table-detail").param("database", "sales").param("table", "order_item")
                .header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stats").exists());
        String explainId = UUID.randomUUID().toString();
        MvcResult explained = mvc.perform(post("/internal/api/v1/agent/data-sources/" + id + "/sql/explain")
                .header("Authorization", "Bearer token-a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionId\":\"" + explainId + "\",\"sql\":\"SELECT * FROM order_item\",\"database\":\"sales\"}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(explained)).andExpect(status().isOk()).andExpect(jsonPath("$.supported").value(true));
        String queryId = UUID.randomUUID().toString();
        MvcResult queried = mvc.perform(post("/internal/api/v1/agent/data-sources/" + id + "/sql/query")
                .header("Authorization", "Bearer token-a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionId\":\"" + queryId + "\",\"sql\":\"SELECT * FROM order_item\",\"database\":\"sales\",\"maxRows\":10}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(queried)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("RESULT_SET"));

        mvc.perform(get("/internal/api/v1/agent/data-sources/" + id + "/table-detail")
                .param("database", "sales").param("table", "customer").header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uniqueKeys[*].name").value(org.hamcrest.Matchers.hasItems("uk_rel_email", "ux_rel_code")))
            .andExpect(jsonPath("$.coverage.uniqueKeys.status").value("COMPLETE"));
        MvcResult child = mvc.perform(get("/internal/api/v1/agent/data-sources/" + id + "/relationships")
                .param("database", "sales").param("table", "child_rel").param("direction", "OUTBOUND")
                .header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.coverage").value("COMPLETE"))
            .andReturn();
        String childJson = child.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(childJson).contains("customer").contains("parent_comp").contains("FOREIGN_KEY");
        mvc.perform(get("/internal/api/v1/agent/data-sources/" + id + "/relationships")
                .param("database", "sales").param("table", "lookalike").param("direction", "BOTH")
                .header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.coverage").value("COMPLETE"));
        MvcResult inbound = mvc.perform(get("/internal/api/v1/agent/data-sources/" + id + "/relationships")
                .param("database", "sales").param("table", "customer").param("direction", "INBOUND").param("pageSize", "2")
                .header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.coverage").value("TRUNCATED"))
            .andReturn();
        String token = json.readTree(inbound.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).path("nextPageToken").asText();
        mvc.perform(get("/internal/api/v1/agent/data-sources/" + id + "/relationships")
                .param("database", "sales").param("table", "customer").param("direction", "INBOUND")
                .param("pageSize", "2").param("pageToken", token).header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
        mvc.perform(get("/api/v1/data-sources/" + id + "/table-detail").param("database", "sales").param("table", "customer")
                .header("Authorization", "Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uniqueKeys").doesNotExist());
    }

    private void executeSelect(String dataSourceId, String database, String sql) throws Exception {
        String executionId = UUID.randomUUID().toString();
        String body = "{\"executionId\":\"" + executionId + "\",\"dataSourceId\":\"" + dataSourceId
            + "\",\"database\":\"" + database + "\",\"statement\":" + json.writeValueAsString(sql) + ",\"rowLimit\":100}";
        MvcResult started = mvc.perform(post("/api/v1/sql/executions").header("Authorization", "Bearer token-a")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("RESULT_SET"));
    }

    private static String payload(String password) {
        return "{\"name\":\"PG 订单库\",\"engine\":\"POSTGRESQL\",\"host\":\"127.0.0.1\",\"port\":"
            + POSTGRES.getMappedPort(5432) + ",\"username\":\"" + POSTGRES.getUsername()
            + "\",\"password\":\"" + password + "\",\"defaultDatabase\":\"orders\",\"sslMode\":\"DISABLED\","
            + "\"connectTimeoutSeconds\":10,\"properties\":{\"ApplicationName\":\"zorth-sql-editor\"}}";
    }

    private static String testPayload(String password) {
        return "{\"engine\":\"POSTGRESQL\",\"host\":\"127.0.0.1\",\"port\":" + POSTGRES.getMappedPort(5432)
            + ",\"username\":\"" + POSTGRES.getUsername() + "\",\"password\":\"" + password
            + "\",\"defaultDatabase\":\"orders\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,"
            + "\"properties\":{\"ApplicationName\":\"zorth-sql-editor\"}}";
    }

    private static String context(String user, String product, String productName) {
        return "{\"userId\":\"" + user + "\",\"username\":\"" + user + "\",\"displayName\":\"" + user
            + "\",\"product\":{\"id\":\"" + product + "\",\"name\":\"" + productName
            + "\"},\"tokenExpiresAt\":\"2099-01-01T00:00:00Z\"}";
    }
}
