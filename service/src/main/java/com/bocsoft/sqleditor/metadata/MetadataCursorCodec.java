package com.bocsoft.sqleditor.metadata;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MetadataCursorCodec {
    private final ObjectMapper mapper;private final byte[] key;private final int max;
    public MetadataCursorCodec(ObjectMapper mapper,SqlEditorProperties properties){this.mapper=mapper;this.key=Base64.getDecoder().decode(properties.getCursor().getSigningKey());this.max=properties.getCursor().getMaximumTokenLength();}
    public String encode(String after,String scope){try{Payload p=new Payload();p.v=1;p.after=after;p.scope=sha(scope);byte[] b=mapper.writeValueAsBytes(p);return Base64.getUrlEncoder().withoutPadding().encodeToString(b)+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(sign(b));}catch(Exception e){throw invalid();}}
    public String decode(String token,String scope){if(token==null||token.isEmpty())return null;if(token.length()>max)throw invalid();try{String[] x=token.split("\\.",-1);if(x.length!=2)throw invalid();byte[] b=Base64.getUrlDecoder().decode(x[0]);if(!MessageDigest.isEqual(sign(b),Base64.getUrlDecoder().decode(x[1])))throw invalid();Payload p=mapper.readValue(b,Payload.class);if(p.v!=1||p.after==null||!sha(scope).equals(p.scope))throw invalid();return p.after;}catch(ApiException e){throw e;}catch(Exception e){throw invalid();}}
    private byte[] sign(byte[] b)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(b);}private String sha(String v)throws Exception{return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}private ApiException invalid(){return ApiException.validation("pageToken","INVALID","分页游标无效");}
    public static class Payload{public int v;public String after;public String scope;}
}
