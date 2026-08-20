package com.bocsoft.sqleditor.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.auth.AuthContextFilter;
import com.bocsoft.sqleditor.auth.AuthContextResolver;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.GlobalExceptionHandler;
import com.bocsoft.sqleditor.common.RequestIdFilter;
import com.bocsoft.sqleditor.engine.gbase8a.Gbase8aEngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import java.time.Instant;
import java.util.Collections;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerExceptionResolver;

class EngineCatalogTest {
    private final EngineRegistry registry = new EngineRegistry(
        java.util.Arrays.asList(
            new MysqlEngineSupport(),
            new com.bocsoft.sqleditor.engine.postgres.PostgresEngineSupport(),
            new Gbase8aEngineSupport(new MysqlEngineSupport())));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void authenticatedCatalogReturnsMysqlThenPostgresqlThenGbase8a() throws Exception {
        AuthContext context = new AuthContext("u", "user", "User", "p", "Product", Instant.now().plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(context, null, Collections.emptyList()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EngineCatalogController(registry))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
        mvc.perform(get("/api/v1/engines").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(3))
            .andExpect(jsonPath("$.items[0].id").value("MYSQL"))
            .andExpect(jsonPath("$.items[0].family").value("MYSQL_WIRE"))
            .andExpect(jsonPath("$.items[0].defaultPort").value(3306))
            .andExpect(jsonPath("$.items[0].editorLanguage").value("mysql"))
            .andExpect(jsonPath("$.items[0].identifierQuote").value("`"))
            .andExpect(jsonPath("$.items[0].connectionFields[4].name").value("defaultDatabase"))
            .andExpect(jsonPath("$.items[0].connectionFields[4].kind").value("DEFAULT_NAMESPACE"))
            .andExpect(jsonPath("$.items[0].resourceTree[0].kind").value("NAMESPACE"))
            .andExpect(jsonPath("$.items[0].resourceTree[0].listEndpoint").value("databases"))
            .andExpect(jsonPath("$.items[1].id").value("POSTGRESQL"))
            .andExpect(jsonPath("$.items[1].family").value("POSTGRES_WIRE"))
            .andExpect(jsonPath("$.items[1].defaultPort").value(5432))
            .andExpect(jsonPath("$.items[1].editorLanguage").value("pgsql"))
            .andExpect(jsonPath("$.items[1].identifierQuote").value("\""))
            .andExpect(jsonPath("$.items[1].capabilities.defaultNamespaceRequired").value(true))
            .andExpect(jsonPath("$.items[1].connectionFields[4].required").value(true))
            .andExpect(jsonPath("$.items[1].connectionFields[4].label").value("数据库名"))
            .andExpect(jsonPath("$.items[1].resourceTree[0].kind").value("NAMESPACE"))
            .andExpect(jsonPath("$.items[1].resourceTree[0].label").value("模式"))
            .andExpect(jsonPath("$.items[1].resourceTree[0].listEndpoint").value("databases"))
            .andExpect(jsonPath("$.items[2].id").value("GBASE_8A"))
            .andExpect(jsonPath("$.items[2].displayName").value("GBase 8a"))
            .andExpect(jsonPath("$.items[2].family").value("MYSQL_WIRE"))
            .andExpect(jsonPath("$.items[2].defaultPort").value(5258))
            .andExpect(jsonPath("$.items[2].editorLanguage").value("mysql"))
            .andExpect(jsonPath("$.items[2].identifierQuote").value("`"))
            .andExpect(jsonPath("$.items[2].capabilities.defaultNamespaceRequired").value(false))
            .andExpect(jsonPath("$.items[2].connectionFields[1].defaultValue").value("5258"))
            .andExpect(jsonPath("$.items[2].connectionFields[4].required").value(false))
            .andExpect(jsonPath("$.items[2].resourceTree[0].kind").value("NAMESPACE"))
            .andExpect(jsonPath("$.items[2].resourceTree[0].label").value("数据库"))
            .andExpect(jsonPath("$.items[2].resourceTree[0].listEndpoint").value("databases"))
            .andExpect(jsonPath("$.items[0].password").doesNotExist())
            .andExpect(jsonPath("$.items[0].jdbcUrl").doesNotExist());
    }

    @Test void unauthenticatedCatalogIs401() throws Exception {
        AuthContextResolver resolver = mock(AuthContextResolver.class);
        HandlerExceptionResolver errors = (request, response, handler, exception) -> {
            ((HttpServletResponse) response).setStatus(((ApiException) exception).getStatus().value());
            return null;
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EngineCatalogController(registry))
            .addFilters(new AuthContextFilter(resolver, errors))
            .build();
        mvc.perform(get("/api/v1/engines")).andExpect(status().isUnauthorized());
        verifyNoInteractions(resolver);
    }
}
