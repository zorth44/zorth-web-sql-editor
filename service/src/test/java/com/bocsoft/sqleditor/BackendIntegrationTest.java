package com.bocsoft.sqleditor;

import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.DynamicPoolManager;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Base64;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class BackendIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("sql_editor_test").withUsername("editor").withPassword("editor-password");
    static final WireMockServer AUTH=new WireMockServer(0);
    static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;@Autowired DynamicPoolManager pools;
    @Autowired com.bocsoft.sqleditor.config.SqlEditorProperties editorProperties;

    static { AUTH.start(); }
    @BeforeAll static void auth(){configureFor("localhost",AUTH.port());stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context")).withHeader("Authorization",equalTo("Bearer token-a")).willReturn(okJson(context("user-a","product-a","产品 A"))));stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context")).withHeader("Authorization",equalTo("Bearer token-b")).willReturn(okJson(context("user-b","product-b","产品 B"))));stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context")).atPriority(10).willReturn(unauthorized().withHeader("Content-Type","application/json").withBody("{\"code\":\"UNAUTHENTICATED\"}")));}
    @AfterAll static void stopAuth(){AUTH.stop();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",MYSQL::getJdbcUrl);registry.add("spring.datasource.username",MYSQL::getUsername);registry.add("spring.datasource.password",MYSQL::getPassword);registry.add("sql-editor.auth.context-url",()->AUTH.baseUrl()+"/internal/api/v1/auth/context");registry.add("sql-editor.auth.internal-service-key",()->"integration-service-key");registry.add("sql-editor.auth.cache-ttl-seconds",()->0);registry.add("sql-editor.credentials.current-version",()->"v1");registry.add("sql-editor.credentials.keys.v1",()->KEY);registry.add("sql-editor.cursor.signing-key",()->KEY);registry.add("sql-editor.network.allowed-cidrs[0]",()->"127.0.0.0/8");registry.add("sql-editor.network.allowed-cidrs[1]",()->"::1/128");registry.add("sql-editor.network.denied-cidrs[0]",()->"192.0.2.0/24");registry.add("management.server.port",()->"-1");}

    @Test void fullProductScopedCrudAndConnectionContract()throws Exception{
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-Id")).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/session").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andExpect(jsonPath("$.product.id").value("product-a")).andExpect(jsonPath("$.capabilities[0]").value("DATA_SOURCE_MANAGE")).andExpect(jsonPath("$.capabilities").value(org.hamcrest.Matchers.hasItem("SCRIPT_MANAGE")));
        JsonNode a=create("token-a","共享名称");JsonNode b=create("token-b","共享名称");String idA=a.path("id").asText();String idB=b.path("id").asText();assertThat(idA).isNotEqualTo(idB);assertThat(a.toString()).doesNotContain(MYSQL.getPassword()).doesNotContain("productId").doesNotContain("passwordCiphertext");
        assertThat(Instant.parse(a.path("createdAt").asText()).toString()).isNotBlank();assertThat(jdbc.queryForObject("select @@session.time_zone",String.class)).isEqualTo("+00:00");
        mvc.perform(get("/api/v1/data-sources").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(idA));
        mvc.perform(get("/api/v1/data-sources/"+idA).header("Authorization","Bearer token-b")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DATA_SOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v1/data-sources/"+idA+":test").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(post("/api/v1/data-sources/"+idA+":test").header("Authorization","Bearer token-b")).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/data-sources/"+idA+":test").header("Authorization","Bearer token-b").contentType(MediaType.APPLICATION_JSON).content(connectionPayload(""))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/data-sources:test").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(connectionPayload(MYSQL.getPassword()))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        mvc.perform(post("/api/v1/data-sources:test").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(connectionPayload("wrong-database-password"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value("AUTHENTICATION_FAILED")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wrong-database-password"))));
        mvc.perform(post("/api/v1/data-sources:test").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(connectionPayloadDatabase(MYSQL.getPassword(),"missing_database"))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.failureCode").value("DATABASE_NOT_FOUND"));
        mvc.perform(post("/api/v1/data-sources:test").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(connectionPayloadPort(MYSQL.getPassword(),MYSQL.getMappedPort(3306)+1))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(post("/api/v1/data-sources/"+idA+":test").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(connectionPayload(""))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUCCESS"));
        assertThat(pools.size()).isZero();
        try(Connection ignored=pools.borrow(idA,1,new ConnectionConfiguration("127.0.0.1",MYSQL.getMappedPort(3306),MYSQL.getUsername(),MYSQL.getPassword(),MYSQL.getDatabaseName(),"DISABLED",10,Collections.singletonMap("serverTimezone","UTC")))){assertThat(ignored.isValid(2)).isTrue();}
        assertThat(pools.size()).isEqualTo(1);
        pools.invalidate(idA);ExecutorService executor=Executors.newFixedThreadPool(4);try{ConnectionConfiguration configuration=new ConnectionConfiguration("127.0.0.1",MYSQL.getMappedPort(3306),MYSQL.getUsername(),MYSQL.getPassword(),MYSQL.getDatabaseName(),"DISABLED",10,Collections.singletonMap("serverTimezone","UTC"));List<Callable<Boolean>> calls=new ArrayList<Callable<Boolean>>();for(int i=0;i<4;i++)calls.add(()->{try(Connection connection=pools.borrow(idA,1,configuration)){return connection.isValid(2);}});for(Future<Boolean> future:executor.invokeAll(calls))assertThat(future.get()).isTrue();}finally{executor.shutdownNow();}assertThat(pools.size()).isEqualTo(1);
        String updateBase=payload("更新名称","");String update=updateBase.substring(0,updateBase.length()-1)+",\"version\":1}";
        mvc.perform(put("/api/v1/data-sources/"+idA).header("Authorization","Bearer token-b").contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/data-sources/"+idB+"?version=1").header("Authorization","Bearer token-a")).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/data-sources/"+idA).header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.name").value("更新名称"));
        assertThat(pools.size()).isZero();
        mvc.perform(put("/api/v1/data-sources/"+idA).header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT")).andExpect(jsonPath("$.details.currentVersion").value(2));
        assertThat(jdbc.queryForObject("select password_ciphertext from sql_data_source where id=?",String.class,idA)).doesNotContain(MYSQL.getPassword());
        assertThat(jdbc.queryForObject("select version from sql_data_source where id=?",Long.class,idA)).isEqualTo(2L);
        mvc.perform(post("/api/v1/data-sources").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON).content(payloadWithUnknown("third"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors[0].field").value("productId"));
        create("token-a","第三条");create("token-a","百分比%库");mvc.perform(get("/api/v1/data-sources").param("keyword","%").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].name").value("百分比%库"));MvcResult page=mvc.perform(get("/api/v1/data-sources?pageSize=1").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andReturn();String cursor=json.readTree(page.getResponse().getContentAsString()).path("nextPageToken").asText();assertThat(cursor).isNotBlank();mvc.perform(get("/api/v1/data-sources?pageSize=1&pageToken="+cursor).header("Authorization","Bearer token-a")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(delete("/api/v1/data-sources/"+idB+"?version=1").header("Authorization","Bearer token-b")).andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test void openApiAndOperationalSurfacesRemainSecretFree()throws Exception{MvcResult result=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();String openApi=result.getResponse().getContentAsString();assertThat(openApi).contains("\"writeOnly\":true").doesNotContain("passwordCiphertext").doesNotContain("passwordIv").doesNotContain("keyVersion").doesNotContain("internalServiceKey");mvc.perform(get("/actuator/env")).andExpect(status().is4xxClientError());mvc.perform(get("/actuator/configprops")).andExpect(status().is4xxClientError());mvc.perform(get("/actuator/heapdump")).andExpect(status().is4xxClientError());}

    @Test void listsVisibleDatabasesWhenDefaultDatabaseIsOmitted()throws Exception{
        JsonNode created=createWithoutDefaultDatabase("token-a","空默认库");
        String id=created.path("id").asText();
        assertThat(created.path("defaultDatabase").isMissingNode() || created.path("defaultDatabase").isNull()).isTrue();
        mvc.perform(get("/api/v1/data-sources/"+id+"/databases").header("Authorization","Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").doesNotExist())
            .andExpect(jsonPath("$.items[*].name").value(org.hamcrest.Matchers.hasItem(MYSQL.getDatabaseName())));
    }

    @Test void executesTablePreviewImmediatelyAfterListingTablesWhenDefaultDatabaseIsOmitted()throws Exception{
        JsonNode created=createWithoutDefaultDatabase("token-a","空默认库预览");
        String id=created.path("id").asText();
        String database=MYSQL.getDatabaseName();
        mvc.perform(get("/api/v1/data-sources/"+id+"/tables").param("database",database).header("Authorization","Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray());
        executeSelect(id,database,"SELECT * FROM `"+database+"`.`sql_data_source`");
        executeSelect(id,database,"SELECT * FROM `"+database+"`.`sql_data_source`");
    }

    @Test void gbase8aRegistersWithoutOpeningMysqlLiveJdbc()throws Exception{
        mvc.perform(get("/api/v1/engines").header("Authorization","Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[2].id").value("GBASE_8A"))
            .andExpect(jsonPath("$.items[2].family").value("MYSQL_WIRE"))
            .andExpect(jsonPath("$.items[2].defaultPort").value(5258))
            .andExpect(jsonPath("$.items[2].resourceTree[0].label").value("数据库"));
        JsonNode created=json.readTree(mvc.perform(post("/api/v1/data-sources").header("Authorization","Bearer token-a")
            .contentType(MediaType.APPLICATION_JSON).content(gbase8aPayload("GBase 8a 源")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        assertThat(created.path("engine").asText()).isEqualTo("GBASE_8A");
        assertThat(created.path("defaultDatabase").isMissingNode() || created.path("defaultDatabase").isNull()).isTrue();
    }

    private void executeSelect(String dataSourceId,String database,String sql)throws Exception{
        String executionId=java.util.UUID.randomUUID().toString();
        MvcResult started=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+executionId+"\",\"dataSourceId\":\""+dataSourceId+"\",\"database\":\""+database+"\",\"statement\":\""+sql+"\",\"rowLimit\":100}"))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.kind").value("RESULT_SET"))
            .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test void agentReadOnlySourceTimeoutAndClientIp()throws Exception{
        JsonNode created=create("token-a","Agent 只读源");
        String id=created.path("id").asText();
        String database=MYSQL.getDatabaseName();
        String insertId=java.util.UUID.randomUUID().toString();
        MvcResult rejected=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a")
            .header("X-Forwarded-For","203.0.113.9").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+insertId+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"INSERT INTO t VALUES (1)\",\"readOnly\":true}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(rejected)).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("READ_ONLY_VIOLATION"));
        assertThat(jdbc.queryForObject("select count(*) from sql_execution_history where id=?",Integer.class,insertId)).isZero();

        String selectId=java.util.UUID.randomUUID().toString();
        MvcResult selected=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a")
            .header("X-Forwarded-For","203.0.113.9").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+selectId+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"SELECT 1\",\"readOnly\":true,\"source\":\"AI_AGENT\",\"timeoutSeconds\":10}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(selected)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("RESULT_SET"));
        mvc.perform(get("/api/v1/sql/history/"+selectId).header("Authorization","Bearer token-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source").value("AI_AGENT"))
            .andExpect(jsonPath("$.clientIp").doesNotExist())
            .andExpect(jsonPath("$.client_ip").doesNotExist());
        assertThat(jdbc.queryForObject("select source from sql_execution_history where id=?",String.class,selectId)).isEqualTo("AI_AGENT");
        String storedIp=jdbc.queryForObject("select client_ip from sql_execution_history where id=?",String.class,selectId);
        assertThat(storedIp).isNotBlank().isNotEqualTo("203.0.113.9");

        String withId=java.util.UUID.randomUUID().toString();
        MvcResult withCte=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+withId+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"WITH x AS (SELECT 1 AS n) SELECT n FROM x\",\"readOnly\":true}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(withCte)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("RESULT_SET"));

        String showId=java.util.UUID.randomUUID().toString();
        MvcResult shown=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+showId+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"SHOW TABLES\",\"readOnly\":true}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(shown)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("RESULT_SET"));

        jdbc.update("create table if not exists zorth_agent_ro_probe (id int primary key)");
        String writeId=java.util.UUID.randomUUID().toString();
        MvcResult written=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+writeId+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"INSERT INTO zorth_agent_ro_probe(id) VALUES (1)\"}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(written)).andExpect(status().isOk()).andExpect(jsonPath("$.kind").value("UPDATE_COUNT"));
        mvc.perform(get("/api/v1/sql/history/"+writeId).header("Authorization","Bearer token-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.source").value("WEB_SQL_EDITOR"));

        String badTimeout=java.util.UUID.randomUUID().toString();
        mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+badTimeout+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"SELECT 1\",\"timeoutSeconds\":61}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.details.fieldErrors[0].field").value("timeoutSeconds"));
        String badSource=java.util.UUID.randomUUID().toString();
        MvcResult invalidSource=mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+badSource+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"SELECT 1\",\"source\":\"BROWSER\"}"))
            .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(invalidSource)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/sql/executions").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"executionId\":\""+java.util.UUID.randomUUID()+"\",\"dataSourceId\":\""+id+"\",\"database\":\""+database+"\",\"statement\":\"SELECT 1\",\"agent\":true}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fieldErrors[0].field").value("agent"));
    }

    @Test void currentUserSqlScriptsArePrivateSearchableAndRenamable()throws Exception{
        JsonNode source=create("token-a","脚本数据源");
        String sourceId=source.path("id").asText();
        String database=MYSQL.getDatabaseName();
        JsonNode created=json.readTree(mvc.perform(post("/api/v1/sql/scripts").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content(scriptPayload("月报",sourceId,database,"select 1 from orders")))
            .andExpect(status().isCreated()).andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("/api/v1/sql/scripts/")))
            .andExpect(jsonPath("$.name").value("月报")).andExpect(jsonPath("$.statement").value("select 1 from orders"))
            .andExpect(jsonPath("$.connectionAvailable").value(true)).andExpect(jsonPath("$.version").value(1)).andReturn()
            .getResponse().getContentAsString());
        String id=created.path("id").asText();
        mvc.perform(get("/api/v1/sql/scripts").header("Authorization","Bearer token-b")).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/sql/scripts/"+id).header("Authorization","Bearer token-b")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SCRIPT_NOT_FOUND"));
        mvc.perform(put("/api/v1/sql/scripts/"+id).header("Authorization","Bearer token-b").contentType(MediaType.APPLICATION_JSON)
            .content(scriptPayload("偷改",sourceId,database,"select 2"))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/sql/scripts/"+id+"?version=1").header("Authorization","Bearer token-b")).andExpect(status().isNotFound());

        mvc.perform(post("/api/v1/sql/scripts").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"空\",\"statement\":\"   \"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        int originalBytes=editorProperties.getExecution().getMaxStatementBytes();
        editorProperties.getExecution().setMaxStatementBytes(8);
        try{
            mvc.perform(post("/api/v1/sql/scripts").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"过大\",\"statement\":\"select 12\"}")).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("STATEMENT_TOO_LARGE"));
        }finally{editorProperties.getExecution().setMaxStatementBytes(originalBytes);}
        mvc.perform(post("/api/v1/sql/scripts").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content(scriptPayload("幽灵",java.util.UUID.randomUUID().toString(),database,"select 1")))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("DATA_SOURCE_NOT_FOUND"));

        mvc.perform(put("/api/v1/sql/scripts/"+id).header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content(scriptUpdate("月报对账",sourceId,database,"select 1 from orders",1)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("月报对账")).andExpect(jsonPath("$.version").value(2))
            .andExpect(jsonPath("$.statement").value("select 1 from orders"));
        mvc.perform(put("/api/v1/sql/scripts/"+id).header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content(scriptUpdate("冲突",sourceId,database,"select 1 from orders",1)))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("VERSION_CONFLICT")).andExpect(jsonPath("$.details.currentVersion").value(2));

        json.readTree(mvc.perform(post("/api/v1/sql/scripts").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
            .content(scriptPayload("月报对账",sourceId,database,"select count(*) from orders")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        mvc.perform(get("/api/v1/sql/scripts").param("keyword","月报对账").header("Authorization","Bearer token-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/v1/sql/scripts").param("keyword","count(*)").header("Authorization","Bearer token-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        MvcResult page=mvc.perform(get("/api/v1/sql/scripts?pageSize=1").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andReturn();
        String cursor=json.readTree(page.getResponse().getContentAsString()).path("nextPageToken").asText();
        assertThat(cursor).isNotBlank();
        mvc.perform(get("/api/v1/sql/scripts?pageSize=1&pageToken="+cursor).header("Authorization","Bearer token-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));

        mvc.perform(delete("/api/v1/data-sources/"+sourceId+"?version="+source.path("version").asLong()).header("Authorization","Bearer token-a"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sql/scripts/"+id).header("Authorization","Bearer token-a"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.statement").value("select 1 from orders"))
            .andExpect(jsonPath("$.connectionAvailable").value(false));

        int originalQuota=editorProperties.getScripts().getMaxPerUser();
        editorProperties.getScripts().setMaxPerUser(2);
        try{
            mvc.perform(post("/api/v1/sql/scripts").header("Authorization","Bearer token-a").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"第三\",\"statement\":\"select 3\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SCRIPT_QUOTA_EXCEEDED"));
        }finally{editorProperties.getScripts().setMaxPerUser(originalQuota);}

        mvc.perform(delete("/api/v1/sql/scripts/"+id+"?version=2").header("Authorization","Bearer token-a")).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/sql/scripts/"+id).header("Authorization","Bearer token-a")).andExpect(status().isNotFound());
    }

    private JsonNode create(String token,String name)throws Exception{MvcResult result=mvc.perform(post("/api/v1/data-sources").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload(name,MYSQL.getPassword()))).andExpect(status().isCreated()).andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("/api/v1/data-sources/"))).andReturn();return json.readTree(result.getResponse().getContentAsString());}
    private JsonNode createWithoutDefaultDatabase(String token,String name)throws Exception{MvcResult result=mvc.perform(post("/api/v1/data-sources").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payloadWithoutDefaultDatabase(name,MYSQL.getPassword()))).andExpect(status().isCreated()).andReturn();return json.readTree(result.getResponse().getContentAsString());}
    private static String payload(String name,String password){return "{\"name\":\""+name+"\",\"engine\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":"+MYSQL.getMappedPort(3306)+",\"username\":\""+MYSQL.getUsername()+"\",\"password\":\""+password+"\",\"defaultDatabase\":\""+MYSQL.getDatabaseName()+"\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{\"serverTimezone\":\"UTC\"},\"description\":\"integration\"}";}
    private static String payloadWithoutDefaultDatabase(String name,String password){return "{\"name\":\""+name+"\",\"engine\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":"+MYSQL.getMappedPort(3306)+",\"username\":\""+MYSQL.getUsername()+"\",\"password\":\""+password+"\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{\"serverTimezone\":\"UTC\"},\"description\":\"integration\"}";}
    private static String gbase8aPayload(String name){return "{\"name\":\""+name+"\",\"engine\":\"GBASE_8A\",\"host\":\"127.0.0.1\",\"port\":5258,\"username\":\"gbase\",\"password\":\"secret\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{\"serverTimezone\":\"UTC\"},\"description\":\"gbase8a\"}";}
    private static String connectionPayload(String password){return "{\"host\":\"127.0.0.1\",\"port\":"+MYSQL.getMappedPort(3306)+",\"username\":\""+MYSQL.getUsername()+"\",\"password\":\""+password+"\",\"defaultDatabase\":\""+MYSQL.getDatabaseName()+"\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{\"serverTimezone\":\"UTC\"}}";}
    private static String connectionPayloadDatabase(String password,String database){return connectionPayload(password).replace("\"defaultDatabase\":\""+MYSQL.getDatabaseName()+"\"","\"defaultDatabase\":\""+database+"\"");}
    private static String connectionPayloadPort(String password,int port){return connectionPayload(password).replace("\"port\":"+MYSQL.getMappedPort(3306),"\"port\":"+port);}
    private static String payloadWithUnknown(String name){String base=payload(name,MYSQL.getPassword());return base.substring(0,base.length()-1)+",\"productId\":\"attacker\"}";}
    private static String scriptPayload(String name,String dataSourceId,String database,String statement){return "{\"name\":\""+name+"\",\"dataSourceId\":\""+dataSourceId+"\",\"database\":\""+database+"\",\"statement\":\""+statement+"\"}";}
    private static String scriptUpdate(String name,String dataSourceId,String database,String statement,long version){return scriptPayload(name,dataSourceId,database,statement).replace("}"," ,\"version\":"+version+"}");}
    private static String context(String user,String product,String productName){return "{\"userId\":\""+user+"\",\"username\":\""+user+"\",\"displayName\":\""+user+"\",\"product\":{\"id\":\""+product+"\",\"name\":\""+productName+"\"},\"tokenExpiresAt\":\"2099-01-01T00:00:00Z\",\"legacy\":{\"pwd\":\"discard\"}}";}
}
