package com.bocsoft.sqleditor.auth;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class AuthClient {
    private final RestTemplate restTemplate;
    private final SqlEditorProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AuthClient(RestTemplate authRestTemplate, SqlEditorProperties properties,
                      ObjectMapper objectMapper, Clock clock) {
        this.restTemplate = authRestTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public AuthContext resolve(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.set("X-Internal-Service-Key", properties.getAuth().getInternalServiceKey());
        try {
            ResponseEntity<AuthContextResponse> response = restTemplate.exchange(
                properties.getAuth().getContextUrl(), HttpMethod.GET,
                new HttpEntity<Void>(headers), AuthContextResponse.class);
            return project(response.getBody());
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode() == HttpStatus.UNAUTHORIZED) throw ApiException.unauthenticated();
            if (exception.getStatusCode() == HttpStatus.CONFLICT) throw productContextError(exception.getResponseBodyAsString());
            throw ApiException.authUnavailable();
        } catch (RestClientException exception) {
            throw ApiException.authUnavailable();
        }
    }

    private AuthContext project(AuthContextResponse body) {
        if (body == null || !StringUtils.hasText(body.getUserId()) || !StringUtils.hasText(body.getUsername())
            || body.getProduct() == null || !StringUtils.hasText(body.getProduct().getId())
            || !StringUtils.hasText(body.getProduct().getName()) || body.getTokenExpiresAt() == null) {
            throw invalidProductContext(null);
        }
        if (!body.getTokenExpiresAt().isAfter(clock.instant())) throw ApiException.unauthenticated();
        String display = StringUtils.hasText(body.getDisplayName()) ? body.getDisplayName() : body.getUsername();
        return new AuthContext(body.getUserId(), body.getUsername(), display,
            body.getProduct().getId(), body.getProduct().getName(), body.getTokenExpiresAt());
    }

    private ApiException productContextError(String json) {
        Integer count = null;
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode value = root.path("details").path("productCount");
            if (value.isInt() || value.isLong()) count = value.intValue();
        } catch (Exception ignored) {
            // Deliberately discard malformed external error content.
        }
        return invalidProductContext(count);
    }

    private ApiException invalidProductContext(Integer count) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        if (count != null) details.put("productCount", count);
        return new ApiException(HttpStatus.CONFLICT, "USER_PRODUCT_CONTEXT_INVALID",
            "当前用户没有唯一有效产品", details);
    }
}
