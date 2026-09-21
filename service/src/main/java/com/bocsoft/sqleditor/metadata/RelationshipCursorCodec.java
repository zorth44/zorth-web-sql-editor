package com.bocsoft.sqleditor.metadata;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RelationshipCursorCodec {
    static final long TTL_SECONDS = 3600L;
    private final ObjectMapper mapper;
    private final byte[] key;
    private final int max;
    private final Clock clock;

    @Autowired
    public RelationshipCursorCodec(ObjectMapper mapper, SqlEditorProperties properties) {
        this(mapper, properties, Clock.systemUTC());
    }

    RelationshipCursorCodec(ObjectMapper mapper, SqlEditorProperties properties, Clock clock) {
        this.mapper = mapper;
        this.key = Base64.getDecoder().decode(properties.getCursor().getSigningKey());
        this.max = properties.getCursor().getMaximumTokenLength();
        this.clock = clock;
    }

    public String encode(String after, Scope scope) {
        try {
            Payload payload = new Payload();
            payload.v = 1;
            payload.after = after;
            payload.scope = sha(scope.binding());
            payload.exp = Instant.now(clock).plusSeconds(TTL_SECONDS).getEpochSecond();
            byte[] body = mapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(body) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public String decode(String token, Scope scope) {
        if (token == null || token.isEmpty()) return null;
        if (token.length() > max) throw invalid();
        try {
            String[] pieces = token.split("\\.", -1);
            if (pieces.length != 2) throw invalid();
            byte[] body = Base64.getUrlDecoder().decode(pieces[0]);
            if (!MessageDigest.isEqual(sign(body), Base64.getUrlDecoder().decode(pieces[1]))) throw invalid();
            Payload payload = mapper.readValue(body, Payload.class);
            if (payload.v != 1 || payload.after == null || payload.exp < Instant.now(clock).getEpochSecond()
                || !sha(scope.binding()).equals(payload.scope)) {
                throw invalid();
            }
            return payload.after;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    private byte[] sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(body);
    }

    private String sha(String value) throws Exception {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private ApiException invalid() {
        return ApiException.validation("pageToken", "INVALID", "分页游标无效");
    }

    public static final class Scope {
        private final String dataSourceId;
        private final String productId;
        private final String database;
        private final String table;
        private final String direction;
        private final int pageSize;

        public Scope(String dataSourceId, String productId, String database, String table, String direction, int pageSize) {
            this.dataSourceId = dataSourceId;
            this.productId = productId;
            this.database = database;
            this.table = table;
            this.direction = direction;
            this.pageSize = pageSize;
        }

        String binding() {
            return dataSourceId + "|" + productId + "|" + database + "|" + table + "|" + direction + "|" + pageSize;
        }
    }

    public static class Payload {
        public int v;
        public String after;
        public String scope;
        public long exp;
    }
}
