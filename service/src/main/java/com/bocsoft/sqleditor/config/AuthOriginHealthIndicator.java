package com.bocsoft.sqleditor.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component("authOrigin")
public class AuthOriginHealthIndicator implements HealthIndicator {
    private final RestTemplate client;private final SqlEditorProperties properties;
    public AuthOriginHealthIndicator(RestTemplate authRestTemplate,SqlEditorProperties properties){this.client=authRestTemplate;this.properties=properties;}
    @Override public Health health(){HttpHeaders headers=new HttpHeaders();headers.set("X-Internal-Service-Key",properties.getAuth().getInternalServiceKey());try{client.exchange(properties.getAuth().getContextUrl(),HttpMethod.GET,new HttpEntity<Void>(headers),Void.class);return Health.up().build();}catch(HttpStatusCodeException exception){return exception.getRawStatusCode()<500?Health.up().build():Health.down().build();}catch(RestClientException exception){return Health.down().build();}}
}
