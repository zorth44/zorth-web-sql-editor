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

    static { AUTH.start(); }
    @BeforeAll static void auth(){configureFor("localhost",AUTH.port());stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context")).withHeader("Authorization",equalTo("Bearer token-a")).willReturn(okJson(context("user-a","product-a","产品 A"))));stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context")).withHeader("Authorization",equalTo("Bearer token-b")).willReturn(okJson(context("user-b","product-b","产品 B"))));stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/internal/api/v1/auth/context")).atPriority(10).willReturn(unauthorized().withHeader("Content-Type","application/json").withBody("{\"code\":\"UNAUTHENTICATED\"}")));}
    @AfterAll static void stopAuth(){AUTH.stop();}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry){registry.add("spring.datasource.url",MYSQL::getJdbcUrl);registry.add("spring.datasource.username",MYSQL::getUsername);registry.add("spring.datasource.password",MYSQL::getPassword);registry.add("sql-editor.auth.context-url",()->AUTH.baseUrl()+"/internal/api/v1/auth/context");registry.add("sql-editor.auth.internal-service-key",()->"integration-service-key");registry.add("sql-editor.auth.cache-ttl-seconds",()->0);registry.add("sql-editor.credentials.current-version",()->"v1");registry.add("sql-editor.credentials.keys.v1",()->KEY);registry.add("sql-editor.cursor.signing-key",()->KEY);registry.add("sql-editor.network.allowed-cidrs[0]",()->"127.0.0.0/8");registry.add("sql-editor.network.allowed-cidrs[1]",()->"::1/128");registry.add("sql-editor.network.denied-cidrs[0]",()->"192.0.2.0/24");registry.add("management.server.port",()->"-1");}

    @Test void fullProductScopedCrudAndConnectionContract()throws Exception{
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-Id")).andExpect(jsonPath("$.requestId").isNotEmpty()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/v1/session").header("Authorization","Bearer token-a")).andExpect(status().isOk()).andExpect(jsonPath("$.product.id").value("product-a")).andExpect(jsonPath("$.capabilities[0]").value("DATA_SOURCE_MANAGE"));
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

    private JsonNode create(String token,String name)throws Exception{MvcResult result=mvc.perform(post("/api/v1/data-sources").header("Authorization","Bearer "+token).contentType(MediaType.APPLICATION_JSON).content(payload(name,MYSQL.getPassword()))).andExpect(status().isCreated()).andExpect(header().string("Location",org.hamcrest.Matchers.startsWith("/api/v1/data-sources/"))).andReturn();return json.readTree(result.getResponse().getContentAsString());}
    private static String payload(String name,String password){return "{\"name\":\""+name+"\",\"engine\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":"+MYSQL.getMappedPort(3306)+",\"username\":\""+MYSQL.getUsername()+"\",\"password\":\""+password+"\",\"defaultDatabase\":\""+MYSQL.getDatabaseName()+"\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{\"serverTimezone\":\"UTC\"},\"description\":\"integration\"}";}
    private static String connectionPayload(String password){return "{\"host\":\"127.0.0.1\",\"port\":"+MYSQL.getMappedPort(3306)+",\"username\":\""+MYSQL.getUsername()+"\",\"password\":\""+password+"\",\"defaultDatabase\":\""+MYSQL.getDatabaseName()+"\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{\"serverTimezone\":\"UTC\"}}";}
    private static String connectionPayloadDatabase(String password,String database){return connectionPayload(password).replace("\"defaultDatabase\":\""+MYSQL.getDatabaseName()+"\"","\"defaultDatabase\":\""+database+"\"");}
    private static String connectionPayloadPort(String password,int port){return connectionPayload(password).replace("\"port\":"+MYSQL.getMappedPort(3306),"\"port\":"+port);}
    private static String payloadWithUnknown(String name){String base=payload(name,MYSQL.getPassword());return base.substring(0,base.length()-1)+",\"productId\":\"attacker\"}";}
    private static String context(String user,String product,String productName){return "{\"userId\":\""+user+"\",\"username\":\""+user+"\",\"displayName\":\""+user+"\",\"product\":{\"id\":\""+product+"\",\"name\":\""+productName+"\"},\"tokenExpiresAt\":\"2099-01-01T00:00:00Z\",\"legacy\":{\"pwd\":\"discard\"}}";}
}
