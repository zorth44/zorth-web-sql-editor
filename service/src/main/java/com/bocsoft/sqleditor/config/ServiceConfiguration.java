package com.bocsoft.sqleditor.config;

import java.time.Clock;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ServiceConfiguration {
    @Bean
    public Clock clock() { return Clock.systemUTC(); }

    @Bean
    public RestTemplate authRestTemplate(RestTemplateBuilder builder, SqlEditorProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getAuth().getConnectTimeoutMs());
        factory.setReadTimeout(properties.getAuth().getReadTimeoutMs());
        return builder.requestFactory(() -> factory).build();
    }
}
