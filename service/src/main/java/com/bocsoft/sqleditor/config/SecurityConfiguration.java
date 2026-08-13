package com.bocsoft.sqleditor.config;

import com.bocsoft.sqleditor.auth.AuthContextFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthContextFilter filter) throws Exception {
        http.csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS).and()
            .authorizeRequests()
            .antMatchers("/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            .antMatchers("/api/v1/**").authenticated()
            .anyRequest().denyAll().and()
            .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
