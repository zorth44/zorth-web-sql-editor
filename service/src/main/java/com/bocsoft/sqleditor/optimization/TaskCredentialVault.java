package com.bocsoft.sqleditor.optimization;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** Independent task-token key space. Task id and version authenticate purpose and prevent swapping. */
@Component
public class TaskCredentialVault {
    private final OptimizationProperties properties;
    private final SecureRandom random = new SecureRandom();
    public TaskCredentialVault(OptimizationProperties properties) { this.properties = properties; }
    public String encrypt(String taskId, String token, String version) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, taskId, version, iv);
            byte[] secret = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(secret);
        } catch (Exception e) { throw new OptimizationFailure("CREDENTIAL_UNAVAILABLE"); }
    }
    public String decrypt(String taskId, String ciphertext, String version) {
        try {
            String[] parts = ciphertext.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            if (iv.length != 12) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, taskId, version, iv).doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new OptimizationFailure("CREDENTIAL_UNAVAILABLE"); }
    }
    private Cipher cipher(int mode, String taskId, String version, byte[] iv) throws Exception {
        String encoded = properties.getCredentialKeys().get(version);
        if (encoded == null) throw new IllegalArgumentException();
        byte[] key = Base64.getDecoder().decode(encoded);
        if (key.length != 32) throw new IllegalArgumentException();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(("sql-optimization-task-token:v1:" + version + ":" + taskId).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
