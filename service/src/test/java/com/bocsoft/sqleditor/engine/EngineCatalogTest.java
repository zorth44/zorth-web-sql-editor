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
        Collections.<EngineSupport>singletonList(new MysqlEngineSupport()));

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void authenticatedCatalogReturnsExactlyMysql() throws Exception {
        AuthContext context = new AuthContext("u", "user", "User", "p", "Product", Instant.now().plusSeconds(60));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(context, null, Collections.emptyList()));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new EngineCatalogController(registry))
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestIdFilter())
            .build();
        mvc.perform(get("/api/v1/engines").accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("MYSQL"))
            .andExpect(jsonPath("$.items[0].family").value("MYSQL_WIRE"))
            .andExpect(jsonPath("$.items[0].defaultPort").value(3306))
            .andExpect(jsonPath("$.items[0].editorLanguage").value("mysql"))
            .andExpect(jsonPath("$.items[0].connectionFields[4].name").value("defaultDatabase"))
            .andExpect(jsonPath("$.items[0].connectionFields[4].kind").value("DEFAULT_NAMESPACE"))
            .andExpect(jsonPath("$.items[0].resourceTree[0].kind").value("NAMESPACE"))
            .andExpect(jsonPath("$.items[0].resourceTree[0].listEndpoint").value("databases"))
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
