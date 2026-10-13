package com.bocsoft.sqleditor.optimization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class OptimizationDigests {
    private OptimizationDigests() { }
    public static String sha256(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result=new StringBuilder();for(byte b:bytes)result.append(String.format("%02x",b & 0xff));return result.toString();
        } catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}
    }
}
