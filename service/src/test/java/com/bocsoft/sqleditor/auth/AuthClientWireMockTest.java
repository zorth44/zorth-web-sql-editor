package com.bocsoft.sqleditor.auth;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

class AuthClientWireMockTest {
    private WireMockServer server;private SqlEditorProperties properties;private AuthClient client;
    @BeforeEach void start(){server=new WireMockServer(0);server.start();configureFor("localhost",server.port());properties=new SqlEditorProperties();properties.getAuth().setContextUrl(server.baseUrl()+"/internal/api/v1/auth/context");properties.getAuth().setInternalServiceKey("service-key");client=new AuthClient(new RestTemplate(),properties,new ObjectMapper().findAndRegisterModules(),Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"),ZoneOffset.UTC));}
    @AfterEach void stop(){server.stop();}
    @Test void projectsDocumentedFieldsAndHeaders(){stubFor(get(urlEqualTo("/internal/api/v1/auth/context")).willReturn(okJson("{\"userId\":\"1\",\"username\":\"zhangsan\",\"displayName\":\"张三\",\"product\":{\"id\":\"p1\",\"name\":\"产品\"},\"tokenExpiresAt\":\"2026-08-13T01:00:00Z\",\"legacy\":{\"pwd\":\"discard\"}}")));AuthContext context=client.resolve("raw-token");assertThat(context.getProductId()).isEqualTo("p1");verify(getRequestedFor(urlEqualTo("/internal/api/v1/auth/context")).withHeader("Authorization",equalTo("Bearer raw-token")).withHeader("X-Internal-Service-Key",equalTo("service-key")));}
    @Test void mapsInvalidProductAndOutage(){stubFor(get(anyUrl()).willReturn(aResponse().withStatus(409).withHeader("Content-Type","application/json").withBody("{\"code\":\"USER_PRODUCT_CONTEXT_INVALID\",\"details\":{\"productCount\":2}}")));assertThatThrownBy(()->client.resolve("token")).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.getCode()).isEqualTo("USER_PRODUCT_CONTEXT_INVALID");assertThat(e.getDetails().toString()).contains("2");});reset();stubFor(get(anyUrl()).willReturn(serverError()));assertThatThrownBy(()->client.resolve("token")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("AUTH_SERVICE_UNAVAILABLE"));}
    @Test void rejectsExpiredAndMalformedSuccess(){stubFor(get(anyUrl()).willReturn(okJson("{\"userId\":\"1\"}")));assertThatThrownBy(()->client.resolve("token")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("USER_PRODUCT_CONTEXT_INVALID"));}
    @Test void mapsUnauthorizedExpiredAndTransportFailuresWithoutSecrets(){stubFor(get(anyUrl()).willReturn(unauthorized()));assertThatThrownBy(()->client.resolve("raw-secret-token")).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.getCode()).isEqualTo("UNAUTHENTICATED");assertThat(e.toString()).doesNotContain("raw-secret-token").doesNotContain("service-key");});reset();stubFor(get(anyUrl()).willReturn(okJson("{\"userId\":\"1\",\"username\":\"u\",\"product\":{\"id\":\"p\",\"name\":\"P\"},\"tokenExpiresAt\":\"2020-01-01T00:00:00Z\"}")));assertThatThrownBy(()->client.resolve("token")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("UNAUTHENTICATED"));server.stop();assertThatThrownBy(()->client.resolve("token")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo("AUTH_SERVICE_UNAVAILABLE"));}
}
