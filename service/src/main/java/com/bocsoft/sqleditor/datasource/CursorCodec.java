package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class CursorCodec {
    private final ObjectMapper mapper;
    private final byte[] signingKey;
    private final int maximumLength;

    public CursorCodec(ObjectMapper mapper, SqlEditorProperties properties) {
        this.mapper = mapper;
        this.signingKey = decodeSigningKey(properties.getCursor().getSigningKey());
        this.maximumLength = properties.getCursor().getMaximumTokenLength();
    }

    public String encode(Instant updatedAt, String id, String keyword, int pageSize) {
        Payload payload = new Payload();
        payload.v = 1;
        payload.updatedAt = updatedAt.toString();
        payload.id = id;
        payload.pageSize = pageSize;
        payload.keywordHash = hash(normalize(keyword));
        try {
            byte[] body = mapper.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(body) + "."
                + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(body));
        } catch (Exception exception) {
            throw invalid();
        }
    }

    public CursorPosition decode(String token, String keyword, int pageSize) {
        if (token == null || token.length() > maximumLength) throw invalid();
        String[] pieces = token.split("\\.", -1);
        if (pieces.length != 2) throw invalid();
        try {
            byte[] body = Base64.getUrlDecoder().decode(pieces[0]);
            byte[] signature = Base64.getUrlDecoder().decode(pieces[1]);
            if (!MessageDigest.isEqual(sign(body), signature)) throw invalid();
            Payload payload = mapper.readValue(body, Payload.class);
            if (payload.v != 1 || payload.pageSize != pageSize
                || !hash(normalize(keyword)).equals(payload.keywordHash)
                || payload.id == null || payload.id.length() > 36) throw invalid();
            return new CursorPosition(Instant.parse(payload.updatedAt), payload.id);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid();
        }
    }

    static String normalize(String keyword) { return keyword == null ? "" : keyword.trim(); }

    private byte[] sign(byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
        return mac.doFinal(body);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private byte[] decodeSigningKey(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length < 32) throw new IllegalArgumentException();
            return decoded;
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Cursor signing key must be Base64 and at least 256 bits");
        }
    }

    private ApiException invalid() { return ApiException.validation("pageToken", "INVALID", "分页游标无效"); }

    public static class Payload {
        public int v;
        public String updatedAt;
        public String id;
        public int pageSize;
        public String keywordHash;
    }
}
