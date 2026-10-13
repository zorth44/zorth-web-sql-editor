package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.*;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.optimization.api.*;
import com.bocsoft.sqleditor.optimization.persistence.*;
import com.fasterxml.jackson.databind.*;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class OptimizationIntegrationTest {
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.0").withDatabaseName("optimizer_test").withUsername("editor").withPassword("fixture-password");
    static final WireMockServer HTTP=new WireMockServer(0);
    static { HTTP.start(); }
    static final String TASKS="/api/v1/sql-optimization/tasks";
    static final String AGENT="/api/v1/agents/sql-lite-optimizer/chat";
    static final String KEY=Base64.getEncoder().encodeToString(new byte[32]);
    @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired JdbcTemplate jdbc;
    @Autowired OptimizationRepository repository;@Autowired SqlOptimizationService service;@Autowired SqlOptimizationWorker worker;@Autowired OptimizationProperties properties;
    @LocalServerPort int port;
    String ds;
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",MYSQL::getJdbcUrl);r.add("spring.datasource.username",MYSQL::getUsername);r.add("spring.datasource.password",MYSQL::getPassword);
        r.add("sql-editor.auth.context-url",()->HTTP.baseUrl()+"/auth");r.add("sql-editor.auth.cache-ttl-seconds",()->0);
        r.add("sql-editor.optimization.enabled",()->true);r.add("sql-editor.optimization.integration-ready",()->true);
        r.add("sql-editor.optimization.allowed-users",()->"u1,u2");r.add("sql-editor.optimization.allowed-products",()->"p1,p2");
        r.add("sql-editor.optimization.credential-keys.v1",()->KEY);r.add("sql-editor.optimization.cursor-key",()->KEY);
        r.add("sql-editor.optimization.agent-base-url",HTTP::baseUrl);r.add("sql-editor.optimization.scheduler-enabled",()->false);
        r.add("sql-editor.agent-api.internal-caller-key-required",()->true);r.add("sql-editor.agent-api.internal-caller-key",()->"callback-key");
        r.add("management.server.port",()->-1);
    }
    @BeforeEach void setup() throws Exception {
        jdbc.update("DELETE FROM sql_optimization_task");jdbc.update("DELETE FROM sql_execution_history");jdbc.update("DELETE FROM sql_data_source");HTTP.resetAll();
        properties.setEnabled(true);properties.setUserQuota(10);properties.setGlobalQuota(100);properties.setMaxReportBytes(1048576);properties.setCallTimeoutMs(135000);properties.setHeartbeatSeconds(15);properties.setSchedulerEnabled(false);
        auth("fixture-bearer-user-one-secret-token","u1","p1",3600);auth("t2","u2","p1",3600);auth("t3","u1","p2",3600);auth("blocked","denied","p1",3600);
        HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/auth")).atPriority(10).willReturn(unauthorized()));
        HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(AGENT)).willReturn(okJson("{\"structuredOutput\":"+OptimizationBoundaryTest.REPORT+"}")));
        String payload="{\"name\":\"fixture datasource\",\"engine\":\"MYSQL\",\"host\":\"127.0.0.1\",\"port\":"+MYSQL.getMappedPort(3306)+",\"username\":\"editor\",\"password\":\"fixture-password\",\"defaultDatabase\":\"optimizer_test\",\"sslMode\":\"DISABLED\",\"connectTimeoutSeconds\":10,\"properties\":{}}";
        ds=read(mvc.perform(post("/api/v1/data-sources").header("Authorization","Bearer fixture-bearer-user-one-secret-token").contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated()).andReturn()).get("id").asText();
    }
    @AfterAll static void close(){HTTP.stop();}
    static void auth(String token,String user,String product,int ttl){HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo("/auth")).withHeader("Authorization",equalTo("Bearer "+token)).willReturn(okJson("{\"userId\":\""+user+"\",\"username\":\"fixture\",\"displayName\":\"fixture\",\"product\":{\"id\":\""+product+"\",\"name\":\"fixture\"},\"tokenExpiresAt\":\""+Instant.now().plusSeconds(ttl)+"\"}")));}
    AuthContext principal(){return new AuthContext("u1","fixture","fixture","p1","fixture",Instant.now().plusSeconds(3600));}
    CreateTaskRequest input(String sql){CreateTaskRequest r=new CreateTaskRequest();r.setDatasourceId(ds);r.setDatabase("optimizer_test");r.setSql(sql);return r;}
    String key(){return UUID.randomUUID().toString();}
    JsonNode read(MvcResult r)throws Exception{return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));}
    JsonNode create(String sql,String key)throws Exception{return read(mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input(sql))))
        .andExpect(status().isAccepted()).andExpect(header().string("Location",org.hamcrest.Matchers.startsWith(TASKS+"/"))).andReturn());}
    JsonNode detail(String id)throws Exception{return read(mvc.perform(get(TASKS+"/"+id).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isOk()).andReturn());}
    OptimizationAttempt claim(){return repository.claim(Instant.now(),150,180,"fixture-worker");}

    @Test void durableContractNullsIdempotencyAndRetry()throws Exception{
        String key=key();JsonNode accepted=create("SELECT 1\n",key);String id=accepted.get("id").asText();
        JsonNode queued=detail(id);assertEquals("QUEUED",queued.get("status").asText());
        for(String field:Arrays.asList("startedAt","completedAt","sourceTaskId","reportSchemaVersion","report","summary","error"))assertTrue(queued.has(field)&&queued.get(field).isNull(),field);
        assertEquals(id,create("SELECT 1\n",key).get("id").asText());
        mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input("SELECT 2")))).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
        mvc.perform(post(TASKS+"/"+id+"/retry").header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",key())).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_NOT_TERMINAL"));
        OptimizationAttempt a=claim();JsonNode running=detail(id);assertEquals("RUNNING",running.get("status").asText());assertFalse(running.get("startedAt").isNull());assertTrue(running.get("report").isNull());
        worker.execute(a);JsonNode success=detail(id);assertEquals("SUCCEEDED",success.get("status").asText());assertEquals(1,success.get("reportSchemaVersion").asInt());assertEquals(1,success.at("/summary/policyIssueCount").asInt());assertEquals(1,success.at("/summary/performanceIssueCount").asInt());assertEquals(1,success.at("/summary/limitationCount").asInt());assertTrue(success.get("error").isNull());
        assertEquals("SUCCEEDED",create("SELECT 1\n",key).get("status").asText());assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_credential",Integer.class));
        String retryKey=key();JsonNode retry=read(mvc.perform(post(TASKS+"/"+id+"/retry").header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",retryKey)).andExpect(status().isAccepted()).andReturn());
        String retryId=retry.get("id").asText();assertNotEquals(id,retryId);assertEquals(id,detail(retryId).get("sourceTaskId").asText());
        properties.setUserQuota(1);assertEquals(retryId,read(mvc.perform(post(TASKS+"/"+id+"/retry").header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",retryKey)).andExpect(status().isAccepted()).andReturn()).get("id").asText());
        OptimizationAttempt b=claim();assertNotEquals(a.sessionId,b.sessionId);worker.execute(b);
        HTTP.verify(2,postRequestedFor(urlEqualTo(AGENT)));assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_attempt",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_execution_history",Integer.class));
    }
    @Test void isolationLiveVisibilityAndCursorBinding()throws Exception{
        String first=create("SELECT 1",key()).get("id").asText();create("SELECT 2",key());create("SELECT 3",key());
        for(String token:Arrays.asList("t2","t3")){
            mvc.perform(get(TASKS+"/"+first).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());
            mvc.perform(post(TASKS+"/"+first+"/retry").header("Authorization","Bearer "+token).header("Idempotency-Key",key())).andExpect(status().isNotFound());
            mvc.perform(get(TASKS).header("Authorization","Bearer "+token)).andExpect(jsonPath("$.items.length()").value(0));
        }
        JsonNode page=read(mvc.perform(get(TASKS).param("pageSize","1").header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isOk()).andReturn());
        assertFalse(page.get("items").get(0).has("sql"));assertFalse(page.get("items").get(0).has("report"));String cursor=page.get("nextPageToken").asText();
        JsonNode next=read(mvc.perform(get(TASKS).param("pageSize","1").param("pageToken",cursor).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isOk()).andReturn());assertNotEquals(page.at("/items/0/id"),next.at("/items/0/id"));
        mvc.perform(get(TASKS).param("pageSize","1").param("pageToken",cursor).header("Authorization","Bearer t2")).andExpect(status().isBadRequest());
        mvc.perform(get(TASKS).param("pageSize","1").param("status","QUEUED").param("pageToken",cursor).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isBadRequest());
        for(String bad:Arrays.asList("",cursor+"tamper"))mvc.perform(get(TASKS).param("pageSize","1").param("pageToken",bad).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isBadRequest());
        jdbc.update("DELETE FROM sql_data_source WHERE id=?",ds);
        mvc.perform(get(TASKS+"/"+first).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isNotFound());
        mvc.perform(get(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(jsonPath("$.items.length()").value(0));
        worker.execute(claim());assertEquals("DATA_SOURCE_UNAVAILABLE",repository.find(first).errorCode);
    }
    @Test void concurrentQuotaAndIdempotencyAreSerialized()throws Exception{
        properties.setUserQuota(2);properties.setGlobalQuota(2);ExecutorService pool=Executors.newFixedThreadPool(6);
        try{
            List<Callable<String>> calls=new ArrayList<>();for(int i=0;i<6;i++)calls.add(()->{try{return service.create(principal(),input("SELECT 1"),key(),"fixture-bearer-user-one-secret-token",key()).getId();}catch(ApiException e){return e.getCode();}});
            int accepted=0,rejected=0;for(Future<String> f:pool.invokeAll(calls)){if("OPTIMIZATION_QUOTA_EXCEEDED".equals(f.get()))rejected++;else accepted++;}
            assertEquals(2,accepted);assertEquals(4,rejected);assertEquals(2,repository.unfinished(null,null));
            jdbc.update("DELETE FROM sql_optimization_task");String shared=key();calls.clear();
            for(int i=0;i<6;i++)calls.add(()->service.create(principal(),input("SELECT 1"),shared,"fixture-bearer-user-one-secret-token",key()).getId());
            Set<String> ids=new HashSet<>();for(Future<String> f:pool.invokeAll(calls))ids.add(f.get());assertEquals(1,ids.size());assertEquals(1,repository.unfinished(null,null));
            properties.setUserQuota(1);assertEquals(ids.iterator().next(),service.create(principal(),input("SELECT 1"),shared,"fixture-bearer-user-one-secret-token",key()).getId());
        }finally{pool.shutdownNow();}
    }
    @Test void validationAndCapabilityFailClosed()throws Exception{
        mvc.perform(get(TASKS)).andExpect(status().isUnauthorized());mvc.perform(get(TASKS).header("Authorization","Bearer blocked")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/session").header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(jsonPath("$.capabilities").value(org.hamcrest.Matchers.hasItem("SQL_OPTIMIZE")));
        properties.setEnabled(false);mvc.perform(get("/api/v1/session").header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(jsonPath("$.capabilities").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("SQL_OPTIMIZE"))));
        mvc.perform(get(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isForbidden());properties.setEnabled(true);
        for(String sql:Arrays.asList(" ","DELETE FROM x","SELECT 1; SELECT 2","EXPLAIN ANALYZE SELECT 1",new String(new char[20001]).replace('\0','x')))
            mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input(sql)))).andExpect(status().isBadRequest());
        CreateTaskRequest bad=input("SELECT 1");bad.setDatabase("optimizer_test\nother: x");
        mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad))).andExpect(status().isBadRequest());
        mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key","bad").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input("SELECT 1")))).andExpect(status().isBadRequest());
        mvc.perform(get(TASKS).param("status","CANCELLED").header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isBadRequest());
        mvc.perform(get(TASKS).param("pageSize","101").header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isBadRequest());
        auth("near","u1","p1",149);
        mvc.perform(post(TASKS).header("Authorization","Bearer near").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input("SELECT 1")))).andExpect(status().isBadRequest());
        mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input("SELECT 1")))).andExpect(status().isBadRequest());
        mvc.perform(get(TASKS).param("pageSize","NaN").header("Authorization","Bearer fixture-bearer-user-one-secret-token")).andExpect(status().isBadRequest());
        mvc.perform(post(TASKS).header("Authorization","Bearer fixture-bearer-user-one-secret-token").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content("{\"datasourceId\":\""+ds+"\",\"database\":\"optimizer_test\",\"sql\":123}")).andExpect(status().isBadRequest());
        assertEquals(0,repository.unfinished(null,null));
    }
    @Test void competingClaimsRecoveryHeartbeatAndLateResultProtection()throws Exception{
        String id=create("SELECT 1",key()).get("id").asText();ExecutorService pool=Executors.newFixedThreadPool(2);
        OptimizationAttempt a;
        try{List<Future<OptimizationAttempt>> claims=pool.invokeAll(Arrays.asList(()->claim(),()->claim()));List<OptimizationAttempt> won=new ArrayList<>();for(Future<OptimizationAttempt> f:claims)if(f.get()!=null)won.add(f.get());assertEquals(1,won.size());a=won.get(0);}finally{pool.shutdownNow();}
        long version=repository.find(id).version;assertTrue(repository.heartbeat(a,Instant.now(),180));assertEquals(version,repository.find(id).version);
        worker.execute(a);assertEquals(TaskStatus.SUCCEEDED,repository.find(id).status);
        assertFalse(repository.finish(a,Instant.now(),null,null,"INTERNAL_ERROR"));assertEquals(TaskStatus.SUCCEEDED,repository.find(id).status);
        String queued=create("SELECT 2",key()).get("id").asText();
        OptimizationRepository restarted=new OptimizationRepository(jdbc,new org.springframework.jdbc.datasource.DataSourceTransactionManager(jdbc.getDataSource()));
        OptimizationAttempt lost=restarted.claim(Instant.now(),150,180,"restart-worker");assertEquals(queued,lost.task.id);
        jdbc.update("UPDATE sql_optimization_attempt SET lease_until=? WHERE id=?",OptimizationRepository.time(Instant.now().minusSeconds(1)),lost.id);
        repository.maintain(Instant.now(),150);assertEquals("WORKER_LOST",repository.find(queued).errorCode);
        assertFalse(repository.heartbeat(lost,Instant.now(),180));assertFalse(repository.finish(lost,Instant.now(),OptimizationBoundaryTest.CLEAN,new TaskSummary(),null));
        assertNull(claim());assertNull(repository.credential(queued));assertEquals("WORKER_LOST",detail(queued).at("/error/code").asText());
    }
    @Test void credentialsReauthorizationIdentityExpiryAndTamper()throws Exception{
        String id=create("SELECT 1",key()).get("id").asText();String ciphertext=jdbc.queryForObject("SELECT ciphertext FROM sql_optimization_credential WHERE task_id=?",String.class,id);assertFalse(ciphertext.contains("fixture-bearer-user-one-secret-token"));
        auth("fixture-bearer-user-one-secret-token","u1","p1",0);worker.execute(claim());assertEquals("CREDENTIAL_EXPIRED",repository.find(id).errorCode);assertNull(repository.credential(id));HTTP.verify(0,postRequestedFor(urlEqualTo(AGENT)));
        auth("fixture-bearer-user-one-secret-token","u1","p1",3600);id=create("SELECT 2",key()).get("id").asText();auth("fixture-bearer-user-one-secret-token","u1","p2",3600);worker.execute(claim());assertEquals("IDENTITY_CHANGED",repository.find(id).errorCode);
        auth("fixture-bearer-user-one-secret-token","u1","p1",3600);id=create("SELECT 3",key()).get("id").asText();jdbc.update("UPDATE sql_optimization_credential SET ciphertext='broken' WHERE task_id=?",id);worker.execute(claim());assertEquals("CREDENTIAL_UNAVAILABLE",repository.find(id).errorCode);
        id=create("SELECT 4",key()).get("id").asText();jdbc.update("UPDATE sql_optimization_credential SET expires_at=? WHERE task_id=?",OptimizationRepository.time(Instant.now().plusSeconds(100)),id);properties.setEnabled(false);
        SqlOptimizationScheduler disabled=new SqlOptimizationScheduler(properties,repository,worker,java.time.Clock.systemUTC(),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try{disabled.tick();}finally{disabled.close();properties.setEnabled(true);}
        assertEquals("CREDENTIAL_EXPIRED",repository.find(id).errorCode);assertNull(repository.find(id).startedAt);
        for(String task:jdbc.queryForList("SELECT id FROM sql_optimization_task",String.class)){jdbc.update("UPDATE sql_optimization_task SET expires_at=? WHERE id=?",OptimizationRepository.time(Instant.now().minusSeconds(1)),task);}
        repository.maintain(Instant.now(),150);assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_task",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_attempt",Integer.class));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_credential",Integer.class));
    }
    @Test void upstreamFailuresAndLimitationsHaveDifferentTerminalShapes()throws Exception{
        for(String report:Arrays.asList(OptimizationBoundaryTest.CLEAN,"{\"verdict\":\"证据不足\",\"issues\":[],\"suggestions\":[],\"limitations\":[{\"ruleId\":null,\"reason\":\"EXPLAIN 不支持\"}]}")){
            HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(AGENT)).willReturn(okJson("{\"structuredOutput\":"+report+"}")));
            String id=create("SELECT 1",key()).get("id").asText();worker.execute(claim());assertEquals("SUCCEEDED",detail(id).get("status").asText());
        }
        for(int http:new int[]{401,500,200}){
            HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(AGENT)).willReturn(aResponse().withStatus(http).withBody("{\"message\":\"secret upstream SQL text\"}")));
            String id=create("SELECT 1",key()).get("id").asText();worker.execute(claim());JsonNode failed=detail(id);
            assertEquals("FAILED",failed.get("status").asText());assertEquals(http==401?"AGENT_AUTH_FAILED":http==500?"AGENT_UNAVAILABLE":"REPORT_INVALID",failed.at("/error/code").asText());
            for(String field:Arrays.asList("report","summary","reportSchemaVersion"))assertTrue(failed.get(field).isNull());assertFalse(failed.toString().contains("secret upstream"));assertNull(repository.credential(id));
        }
    }
    @Test void waitingHttpHoldsNoMetadataTransactionAndLateHttpCannotOverwriteLostTask()throws Exception{
        HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(AGENT)).willReturn(okJson("{\"structuredOutput\":"+OptimizationBoundaryTest.CLEAN+"}").withFixedDelay(900)));
        String id=create("SELECT 1",key()).get("id").asText();OptimizationAttempt a=claim();ExecutorService pool=Executors.newSingleThreadExecutor();
        try{
            Future<?> running=pool.submit(()->worker.execute(a));long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
            while(HTTP.findAll(postRequestedFor(urlEqualTo(AGENT))).isEmpty()&&System.nanoTime()<until)Thread.yield();
            assertFalse(HTTP.findAll(postRequestedFor(urlEqualTo(AGENT))).isEmpty());
            try(java.sql.Connection c=java.sql.DriverManager.getConnection(MYSQL.getJdbcUrl(),"root",MYSQL.getPassword());java.sql.Statement st=c.createStatement();java.sql.ResultSet rs=st.executeQuery("SELECT COUNT(*) FROM information_schema.innodb_trx")) {
                assertTrue(rs.next());assertEquals(0,rs.getInt(1),"Agent wait must hold no metadata transaction");
            }
            jdbc.update("UPDATE sql_optimization_attempt SET lease_until=? WHERE id=?",OptimizationRepository.time(Instant.now().minusSeconds(1)),a.id);repository.maintain(Instant.now(),150);
            running.get(5,TimeUnit.SECONDS);assertEquals("WORKER_LOST",repository.find(id).errorCode);assertNull(repository.find(id).reportJson);HTTP.verify(1,postRequestedFor(urlEqualTo(AGENT)));
        }finally{pool.shutdownNow();}
    }
    @Test void schedulerBoundsLocalCapacityAndOwnHeartbeatsDoNotBreakCompletion()throws Exception{
        HTTP.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(AGENT)).willReturn(okJson("{\"structuredOutput\":"+OptimizationBoundaryTest.CLEAN+"}").withFixedDelay(1600)));
        properties.setHeartbeatSeconds(1);String a=create("SELECT 1",key()).get("id").asText();String b=create("SELECT 2",key()).get("id").asText();String c=create("SELECT 3",key()).get("id").asText();
        SqlOptimizationScheduler scheduler=new SqlOptimizationScheduler(properties,repository,worker,java.time.Clock.systemUTC(),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        try{
            scheduler.tick();scheduler.tick();assertEquals(2,repository.running());assertEquals(1,repository.queued());
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);
            while(repository.running()>0 && System.nanoTime()<deadline)Thread.sleep(30);
            assertEquals(0,repository.running());assertEquals(TaskStatus.SUCCEEDED,repository.find(a).status);assertEquals(TaskStatus.SUCCEEDED,repository.find(b).status);
            scheduler.tick();deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);while(repository.running()>0&&System.nanoTime()<deadline)Thread.sleep(30);
            assertEquals(TaskStatus.SUCCEEDED,repository.find(c).status);HTTP.verify(3,postRequestedFor(urlEqualTo(AGENT)));
        }finally{scheduler.close();properties.setHeartbeatSeconds(15);}
    }
    @Test void missingEncryptionKeyRollsBackAndGlobalQuotaCoversOtherUsers()throws Exception{
        String configured=properties.getCredentialKeys().remove("v1");
        try{ApiException e=assertThrows(ApiException.class,()->service.create(principal(),input("SELECT 1"),key(),"fixture-bearer-user-one-secret-token",key()));assertEquals("CREDENTIAL_UNAVAILABLE",e.getCode());assertEquals(0,repository.unfinished(null,null));assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_credential",Integer.class));}
        finally{properties.getCredentialKeys().put("v1",configured);}
        properties.setGlobalQuota(1);String replay=key();String id=create("SELECT 1",replay).get("id").asText();
        mvc.perform(post(TASKS).header("Authorization","Bearer t2").header("Idempotency-Key",key()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(input("SELECT 2")))).andExpect(status().isTooManyRequests());
        assertEquals(id,create("SELECT 1",replay).get("id").asText());
        jdbc.update("UPDATE sql_data_source SET engine='UNRECOGNIZED' WHERE id=?",ds);
        assertEquals(id,create("SELECT 1",replay).get("id").asText());
    }
    @Test void hardDeadlineRejectsFinishWithoutWaitingForMaintenance()throws Exception{
        String id=create("SELECT 1",key()).get("id").asText();OptimizationAttempt a=claim();
        assertFalse(repository.finish(a,a.deadline.plusSeconds(1),OptimizationBoundaryTest.CLEAN,new TaskSummary(),null));
        repository.maintain(a.deadline.plusSeconds(1),150);assertEquals("WORKER_LOST",repository.find(id).errorCode);
    }
    @Test void realInternalCallbackRequiresBearerAndKeyAndOnlyPlanExecutes()throws Exception{
        String url="http://127.0.0.1:"+port+"/internal/api/v1/agent/data-sources/"+ds+"/sql/";
        RestTemplate http=new RestTemplate();HttpHeaders headers=new HttpHeaders();headers.setContentType(MediaType.APPLICATION_JSON);headers.set("X-Internal-Service-Key","callback-key");
        String body="{\"sql\":\"SELECT 1\",\"database\":\"optimizer_test\"}";
        assertThrows(org.springframework.web.client.HttpClientErrorException.Unauthorized.class,()->http.postForEntity(url+"validate",new HttpEntity<>(body,headers),String.class));
        headers.setBearerAuth("fixture-bearer-user-one-secret-token");assertEquals(HttpStatus.OK,http.postForEntity(url+"validate",new HttpEntity<>(body,headers),String.class).getStatusCode());
        headers.set("X-Internal-Service-Key","wrong");assertThrows(org.springframework.web.client.HttpClientErrorException.Unauthorized.class,()->http.postForEntity(url+"validate",new HttpEntity<>(body,headers),String.class));headers.set("X-Internal-Service-Key","callback-key");
        String explain="{\"sql\":\"SELECT 1\",\"database\":\"optimizer_test\",\"executionId\":\""+key()+"\"}";
        ResponseEntity<String> response=http.postForEntity(url+"explain",new HttpEntity<>(explain,headers),String.class);assertEquals(HttpStatus.OK,response.getStatusCode());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM sql_execution_history WHERE statement_text NOT LIKE 'EXPLAIN%'",Integer.class));
    }
}
