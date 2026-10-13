package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

class OptimizationBoundaryTest {
    static final String CLEAN = "{\"verdict\":\"未发现问题\",\"issues\":[],\"suggestions\":[],\"limitations\":[]}";
    static final String REPORT = "{\"verdict\":\"有问题\",\"issues\":[{\"id\":\"p1\",\"ruleId\":1,\"category\":\"POLICY\",\"priority\":\"重要\",\"severity\":null,\"evidence\":[{\"kind\":\"sql\",\"content\":\"SELECT *\"}],\"detail\":\"字段列表\"},{\"id\":\"e1\",\"ruleId\":2,\"category\":\"PERFORMANCE\",\"priority\":\"一般\",\"severity\":\"MEDIUM\",\"evidence\":[{\"kind\":\"tool\",\"content\":\"估算计划\"}],\"detail\":\"计划估算\"}],\"suggestions\":[{\"id\":\"s1\",\"issueIds\":[\"p1\",\"e1\"],\"detail\":\"人工评估\"}],\"limitations\":[{\"ruleId\":null,\"reason\":\"索引信息不足\"}]}";
    final ObjectMapper json = new ObjectMapper();
    final OptimizationReportValidator validator = new OptimizationReportValidator(json);
    @Test void validatesCategoryEvidenceReferencesAndTypes() throws Exception {
        assertEquals(1, validator.summary(validator.validate(json.readTree(REPORT))).getPerformanceIssueCount());
        assertEquals(0, validator.summary(validator.validate(json.readTree(CLEAN))).getPolicyIssueCount());
        String[] bad = { REPORT.replace("\"p1\",\"e1\"", "\"missing\""), REPORT.replace("\"id\":\"e1\"", "\"id\":\"p1\""),
            REPORT.replace("\"severity\":null", "\"severity\":\"HIGH\""), REPORT.replace("\"severity\":\"MEDIUM\"", "\"severity\":null"),
            REPORT.replace("\"kind\":\"tool\"", "\"kind\":\"sql\""), REPORT.replace("\"kind\":\"sql\"", "\"kind\":\"tool\""),
            REPORT.replace("\"priority\":\"重要\"", "\"priority\":\"URGENT\""), REPORT.replace("\"ruleId\":1", "\"ruleId\":\"1\""),
            REPORT.replace("\"issues\":[", "\"extra\":1,\"issues\":["), REPORT.replace("\"reason\":\"索引信息不足\"", "\"reason\":\" \"") };
        for (String report : bad) assertEquals("REPORT_INVALID", assertThrows(OptimizationFailure.class, () -> validator.validate(json.readTree(report))).getCode());
        ObjectNode missing = (ObjectNode) json.readTree(CLEAN); missing.remove("suggestions");
        assertThrows(OptimizationFailure.class, () -> validator.validate(missing));
    }
    @Test void rejectsWritableCtesSelectIntoAndExecutableComments() {
        for (String sql : new String[]{"WITH changed AS (DELETE FROM x RETURNING *) SELECT * FROM changed", "WITH x AS (SELECT 1) UPDATE y SET z=1", "SELECT * INTO OUTFILE '/tmp/file' FROM x", "SELECT 1 /*! INTO OUTFILE '/tmp/file' */"})
            assertThrows(com.bocsoft.sqleditor.common.ApiException.class, () -> OptimizationSqlGuard.requireReadOnly(sql, "POSTGRESQL"));
        for (String sql : new String[]{"WITH x AS (SELECT 1) SELECT * FROM x", "SELECT 'delete', `update` FROM x -- INTO\n", "SELECT $$UPDATE x$$", "SELECT 1 /* DELETE x */"})
            assertDoesNotThrow(() -> OptimizationSqlGuard.requireReadOnly(sql, "POSTGRESQL"));
    }
    @Test void engineTriviaCannotHideWritableCtes() {
        assertThrows(com.bocsoft.sqleditor.common.ApiException.class, () -> OptimizationSqlGuard.requireReadOnly("WITH c AS (SELECT 1 # 2) DELETE FROM x", "POSTGRESQL"));
        assertDoesNotThrow(() -> OptimizationSqlGuard.requireReadOnly("SELECT 1 # DELETE FROM x", "MYSQL"));
        assertThrows(com.bocsoft.sqleditor.common.ApiException.class, () -> OptimizationSqlGuard.requireReadOnly("WITH c AS (SELECT 'a\\\\') DELETE FROM x -- '", "POSTGRESQL"));
    }
    @Test void vaultUsesPurposeTaskAndVersionWithAuthenticatedEncryption() {
        OptimizationProperties p = new OptimizationProperties(); p.getCredentialKeys().put("v1", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
        TaskCredentialVault vault = new TaskCredentialVault(p);
        String encrypted = vault.encrypt("task-a", "secret-bearer", "v1");
        assertFalse(encrypted.contains("secret-bearer")); assertEquals("secret-bearer", vault.decrypt("task-a", encrypted, "v1"));
        assertNotEquals(encrypted, vault.encrypt("task-a", "secret-bearer", "v1"));
        assertThrows(OptimizationFailure.class, () -> vault.decrypt("task-b", encrypted, "v1"));
        assertThrows(OptimizationFailure.class, () -> vault.decrypt("task-a", encrypted, "v2"));
        assertThrows(OptimizationFailure.class, () -> vault.decrypt("task-a", encrypted.substring(0, encrypted.length()-3)+"AAA", "v1"));
    }
    @Test void fixedClientUsesStructuredOutputAndNeverRetriesErrors() {
        WireMockServer server = new WireMockServer(0); server.start();
        OptimizationProperties p = new OptimizationProperties(); p.setAgentBaseUrl(server.baseUrl()); p.setCallTimeoutMs(200);
        SqlLiteAgentClient client = new SqlLiteAgentClient(p, json, validator);
        AuthContext auth = new AuthContext("user", "user", "user", "product", "product", java.time.Instant.now().plusSeconds(1000));
        String path = "/api/v1/agents/sql-lite-optimizer/chat";
        try {
            server.stubFor(post(urlEqualTo(path)).willReturn(okJson("{\"message\":\"ignore\",\"structuredOutput\":"+CLEAN+"}")));
            assertEquals("未发现问题", client.analyze("ds", "db", "SELECT 1\n", "session", "request", "secret", auth).getVerdict());
            server.verify(postRequestedFor(urlEqualTo(path)).withHeader("Authorization", equalTo("Bearer secret"))
                .withRequestBody(matchingJsonPath("$.sessionId", equalTo("session")))
                .withRequestBody(matchingJsonPath("$.message", equalTo("datasourceId: ds\ndatabase: db\nSELECT 1\n"))));
            for (int status : new int[]{401, 403, 500, 302}) {
                server.resetAll(); server.stubFor(post(urlEqualTo(path)).willReturn(aResponse().withStatus(status)));
                String code = status==401||status==403 ? "AGENT_AUTH_FAILED" : "AGENT_UNAVAILABLE";
                assertEquals(code, assertThrows(OptimizationFailure.class, () -> client.analyze("ds","db","SELECT 1","s","r","secret",auth)).getCode());
                server.verify(1, postRequestedFor(urlEqualTo(path)));
            }
            server.resetAll(); server.stubFor(post(urlEqualTo(path)).willReturn(okJson("{\"message\":"+CLEAN+"}")));
            assertEquals("REPORT_INVALID", assertThrows(OptimizationFailure.class, () -> client.analyze("ds","db","SELECT 1","s","r","secret",auth)).getCode());
            server.resetAll(); server.stubFor(post(urlEqualTo(path)).willReturn(okJson(CLEAN).withFixedDelay(700)));
            assertEquals("AGENT_TIMEOUT", assertThrows(OptimizationFailure.class, () -> client.analyze("ds","db","SELECT 1","s","r","secret",auth)).getCode());
            server.verify(1, postRequestedFor(urlEqualTo(path)));
            p.setMaxReportBytes(10); server.resetAll(); server.stubFor(post(urlEqualTo(path)).willReturn(okJson(CLEAN)));
            assertEquals("REPORT_TOO_LARGE", assertThrows(OptimizationFailure.class, () -> client.analyze("ds","db","SELECT 1","s","r","secret",auth)).getCode());
            assertThrows(OptimizationFailure.class, () -> SqlLiteAgentClient.message("ds\nother: bad", "db", "SELECT 1"));
        } finally { client.close(); server.stop(); }
    }
}
