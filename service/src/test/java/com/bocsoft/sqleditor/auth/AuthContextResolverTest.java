package com.bocsoft.sqleditor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthContextResolverTest {
    @Test void cachesOnlyByDigestAndReusesValidContext(){AuthClient client=mock(AuthClient.class);AuthContext context=new AuthContext("u","name","Name","p","Product",Instant.parse("2026-08-13T01:00:00Z"));when(client.resolve("raw-secret-token")).thenReturn(context);SqlEditorProperties properties=new SqlEditorProperties();properties.getAuth().setCacheTtlSeconds(60);AuthContextResolver resolver=new AuthContextResolver(client,Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"),ZoneOffset.UTC),properties,new SqlEditorMetrics(new SimpleMeterRegistry()));assertThat(resolver.resolve("raw-secret-token")).isSameAs(context);assertThat(resolver.resolve("raw-secret-token")).isSameAs(context);verify(client,times(1)).resolve("raw-secret-token");}
    @Test void cacheStorageAndMetricTagsNeverContainRawToken()throws Exception{AuthClient client=mock(AuthClient.class);when(client.resolve("raw-secret-token")).thenReturn(new AuthContext("u","name","Name","p","Product",Instant.parse("2099-01-01T00:00:00Z")));SqlEditorProperties properties=new SqlEditorProperties();SimpleMeterRegistry registry=new SimpleMeterRegistry();AuthContextResolver resolver=new AuthContextResolver(client,Clock.systemUTC(),properties,new SqlEditorMetrics(registry));resolver.resolve("raw-secret-token");Field field=AuthContextResolver.class.getDeclaredField("cache");field.setAccessible(true);Object cache=field.get(resolver);String cacheText=cache.toString();assertThat(cacheText).doesNotContain("raw-secret-token");assertThat(registry.getMeters().toString()).doesNotContain("raw-secret-token");}
}
