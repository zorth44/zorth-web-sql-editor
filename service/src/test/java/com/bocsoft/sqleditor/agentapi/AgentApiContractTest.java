package com.bocsoft.sqleditor.agentapi;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bocsoft.sqleditor.agentapi.api.AgentSqlQueryResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentQueryMasking;
import com.bocsoft.sqleditor.agentapi.api.AgentQueryTruncation;
import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.GlobalExceptionHandler;
import com.bocsoft.sqleditor.common.RequestIdFilter;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.api.DataSourceListItemResponse;
import com.bocsoft.sqleditor.execution.SqlExecutionService;
import com.bocsoft.sqleditor.metadata.MetadataService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AgentApiContractTest {
    private MockMvc mvc;
    private DataSourceService dataSources;
    private MetadataService metadata;
    private AgentSqlService sql;

    @BeforeEach
    void setUp() {
        dataSources = org.mockito.Mockito.mock(DataSourceService.class);
        metadata = org.mockito.Mockito.mock(MetadataService.class);
        sql = org.mockito.Mockito.mock(AgentSqlService.class);
        SqlExecutionService executions = org.mockito.Mockito.mock(SqlExecutionService.class);
        com.bocsoft.sqleditor.common.ClientIpResolver clientIps = org.mockito.Mockito.mock(com.bocsoft.sqleditor.common.ClientIpResolver.class);
        when(executions.asyncTimeoutMs(anyInt())).thenReturn(5000L);
        when(clientIps.resolve(any())).thenReturn("127.0.0.1");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(
                new AgentMetadataController(dataSources, metadata, new AgentApiMapper()),
                new AgentSqlController(sql, executions, new SimpleAsyncTaskExecutor(), clientIps))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
            .setValidator(validator)
            .addFilters(new RequestIdFilter())
            .build();
        AuthContext context = new AuthContext("u", "user", "User", "p", "Product", Instant.now().plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(context, null, Collections.emptyList()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsDatasourcesWithoutCredentialFields() throws Exception {
        DataSourceListItemResponse item = new DataSourceListItemResponse();
        item.setId("ds-1");
        item.setName("orders");
        item.setEngine("MYSQL");
        item.setHost("127.0.0.1");
        item.setPort(3306);
        item.setUsername("secret-user");
        item.setPasswordConfigured(true);
        item.setDefaultDatabase("orders");
        when(dataSources.list(any(), eq(""), eq(20), eq(null)))
            .thenReturn(new CursorPage<DataSourceListItemResponse>(Collections.singletonList(item), null));
        mvc.perform(get("/internal/api/v1/agent/data-sources").header("X-Request-Id", UUID.randomUUID().toString()))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-Id"))
            .andExpect(jsonPath("$.items[0].id").value("ds-1"))
            .andExpect(jsonPath("$.items[0].username").doesNotExist())
            .andExpect(jsonPath("$.items[0].passwordConfigured").doesNotExist());
    }

    @Test
    void rejectsUnknownQueryFields() throws Exception {
        mvc.perform(post("/internal/api/v1/agent/data-sources/ds-1/sql/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionId\":\"" + UUID.randomUUID() + "\",\"sql\":\"SELECT 1\",\"readOnly\":false,\"source\":\"WEB_SQL_EDITOR\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
        verify(sql, never()).query(any(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void queryEnvelopeIsReturned() throws Exception {
        String executionId = UUID.randomUUID().toString();
        when(sql.queryTimeoutSeconds(any())).thenReturn(15);
        when(sql.query(any(), eq("ds-1"), any(), anyString(), anyString()))
            .thenReturn(new AgentSqlQueryResponse(executionId, "RESULT_SET", Collections.emptyList(),
                Collections.<java.util.List<Object>>emptyList(), 0L, false,
                new AgentQueryTruncation(false, false, 0), 3L, Collections.<com.bocsoft.sqleditor.agentapi.api.AgentFinding>emptyList(),
                new AgentQueryMasking(false, null), 200, 15, 1024, 4096));
        MvcResult started = mvc.perform(post("/internal/api/v1/agent/data-sources/ds-1/sql/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"executionId\":\"" + executionId + "\",\"sql\":\"SELECT 1\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();
        mvc.perform(asyncDispatch(started))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.executionId").value(executionId))
            .andExpect(jsonPath("$.kind").value("RESULT_SET"))
            .andExpect(jsonPath("$.masking.applied").value(false));
    }
}
