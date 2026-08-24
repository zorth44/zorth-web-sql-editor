package com.bocsoft.sqleditor.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {
    @Test void ignoresForwardedHeadersWhenPeerIsNotTrusted() {
        ClientIpResolver resolver = resolver();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.1.8");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.1.1.8");
        assertThat(resolver.resolve(request)).isEqualTo("10.1.1.8");
    }

    @Test void usesLeftmostForwardedHopFromTrustedProxy() {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getHttp().setTrustedProxyCidrs(Collections.singletonList("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.1.8");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.1.1.8");
        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test void usesRealIpWhenForwardedForIsAbsent() {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getHttp().setTrustedProxyCidrs(Collections.singletonList("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.1.8");
        request.addHeader("X-Real-IP", "198.51.100.2");
        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.2");
    }

    @Test void truncatesToSixtyFourCharacters() {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getHttp().setTrustedProxyCidrs(Collections.singletonList("10.0.0.0/8"));
        ClientIpResolver resolver = new ClientIpResolver(properties);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.1.1.8");
        char[] chars = new char[80];
        Arrays.fill(chars, '1');
        request.addHeader("X-Real-IP", new String(chars));
        assertThat(resolver.resolve(request)).hasSize(64);
    }

    private ClientIpResolver resolver() {
        return new ClientIpResolver(new SqlEditorProperties());
    }
}
