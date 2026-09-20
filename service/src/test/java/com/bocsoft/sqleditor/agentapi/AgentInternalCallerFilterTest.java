package com.bocsoft.sqleditor.agentapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

class AgentInternalCallerFilterTest {
    @Test void keyIsIgnoredWhenNotRequired() throws Exception {
        SqlEditorProperties properties = new SqlEditorProperties();
        AgentInternalCallerFilter filter = new AgentInternalCallerFilter(properties, (req, res, h, e) -> null);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/api/v1/agent/data-sources");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test void requiredKeyIsAdditiveAndDoesNotAcceptBearerAsSubstitute() throws Exception {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getAgentApi().setInternalCallerKeyRequired(true);
        properties.getAgentApi().setInternalCallerKey("agent-caller");
        HandlerExceptionResolver errors = (req, res, h, e) -> {
            ((HttpServletResponse) res).setStatus(((ApiException) e).getStatus().value());
            return null;
        };
        AgentInternalCallerFilter filter = new AgentInternalCallerFilter(properties, errors);
        MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/internal/api/v1/agent/data-sources");
        missing.addHeader("Authorization", "Bearer token-a");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        filter.doFilter(missing, missingResponse, new MockFilterChain());
        assertThat(missingResponse.getStatus()).isEqualTo(401);

        MockHttpServletRequest ok = new MockHttpServletRequest("GET", "/internal/api/v1/agent/data-sources");
        ok.addHeader("Authorization", "Bearer token-a");
        ok.addHeader(AgentInternalCallerFilter.HEADER, "agent-caller");
        MockHttpServletResponse okResponse = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(ok, okResponse, chain);
        assertThat(chain.getRequest()).isNotNull();
    }
}
