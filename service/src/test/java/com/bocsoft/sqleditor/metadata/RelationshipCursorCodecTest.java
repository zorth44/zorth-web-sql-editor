package com.bocsoft.sqleditor.metadata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class RelationshipCursorCodecTest {
    @Test void bindsScopeAndRejectsTamperingAndExpiry() {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        RelationshipCursorCodec codec = new RelationshipCursorCodec(new ObjectMapper(), properties, clock);
        RelationshipCursorCodec.Scope scope = new RelationshipCursorCodec.Scope("ds", "product-a", "sales", "orders", "BOTH", 10);
        String token = codec.encode("outbound\0sales\0parent", scope);
        assertThat(codec.decode(token, scope)).isEqualTo("outbound\0sales\0parent");
        RelationshipCursorCodec.Scope otherProduct = new RelationshipCursorCodec.Scope("ds", "product-b", "sales", "orders", "BOTH", 10);
        assertThatThrownBy(() -> codec.decode(token, otherProduct)).isInstanceOf(ApiException.class);
        RelationshipCursorCodec.Scope otherPage = new RelationshipCursorCodec.Scope("ds", "product-a", "sales", "orders", "BOTH", 20);
        assertThatThrownBy(() -> codec.decode(token, otherPage)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> codec.decode(token.substring(0, token.length() - 2) + "xx", scope)).isInstanceOf(ApiException.class);
        RelationshipCursorCodec expired = new RelationshipCursorCodec(new ObjectMapper(), properties,
            Clock.fixed(Instant.parse("2026-01-01T02:00:01Z"), ZoneOffset.UTC));
        assertThatThrownBy(() -> expired.decode(token, scope)).isInstanceOf(ApiException.class);
    }
}
