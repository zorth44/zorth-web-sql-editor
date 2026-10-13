package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class OptimizationCursorCodec {
    private final OptimizationProperties properties;
    private final ObjectMapper json;
    public OptimizationCursorCodec(OptimizationProperties properties, ObjectMapper json) { this.properties=properties;this.json=json; }
    public String encode(AuthContext auth, String status, int size, Instant at, String id) {
        try {
            byte[] bytes=json.writeValueAsBytes(Arrays.asList("optimization-cursor-v1",auth.getUserId(),auth.getProductId(),status,Integer.toString(size),at.toString(),id));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(bytes));
        } catch(Exception e){throw new IllegalStateException("Optimization cursor key unavailable");}
    }
    public Position decode(String token, AuthContext auth, String status, int size) {
        try {
            if(token.length()>2048)throw new IllegalArgumentException();
            String[] parts=token.split("\\.",-1);if(parts.length!=2)throw new IllegalArgumentException();
            byte[] bytes=Base64.getUrlDecoder().decode(parts[0]);
            if(!MessageDigest.isEqual(sign(bytes),Base64.getUrlDecoder().decode(parts[1])))throw new IllegalArgumentException();
            String[] values=json.readValue(bytes,String[].class);
            if(values.length!=7 || !"optimization-cursor-v1".equals(values[0]) || !auth.getUserId().equals(values[1]) || !auth.getProductId().equals(values[2])
                || !java.util.Objects.equals(status,values[3]) || !Integer.toString(size).equals(values[4]))throw new IllegalArgumentException();
            return new Position(Instant.parse(values[5]),java.util.UUID.fromString(values[6]).toString());
        } catch(Exception e){throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_PAGE_TOKEN","分页游标无效");}
    }
    private byte[] sign(byte[] bytes)throws Exception {
        byte[] key=Base64.getDecoder().decode(properties.getCursorKey());if(key.length<32)throw new IllegalArgumentException();
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(bytes);
    }
    public static class Position {
        public final Instant at;public final String id;
        Position(Instant at,String id){this.at=at;this.id=id;}
    }
}
