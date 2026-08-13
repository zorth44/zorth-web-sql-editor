package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CredentialCipher {
    private static final int IV_BYTES = 12;
    private final String currentVersion;
    private final Map<String, SecretKeySpec> keys;
    private final SecureRandom random;

    @Autowired
    public CredentialCipher(SqlEditorProperties properties) {
        this(properties.getCredentials().getCurrentVersion(), properties.getCredentials().getKeys(), new SecureRandom());
    }

    CredentialCipher(String currentVersion, Map<String, String> configured, SecureRandom random) {
        Map<String, SecretKeySpec> decoded = new LinkedHashMap<String, SecretKeySpec>();
        for (Map.Entry<String, String> entry : configured.entrySet()) {
            byte[] value;
            try { value = Base64.getDecoder().decode(entry.getValue()); }
            catch (IllegalArgumentException exception) { throw new IllegalStateException("Credential key is not valid Base64"); }
            if (value.length != 32) throw new IllegalStateException("Credential keys must be exactly 256 bits");
            decoded.put(entry.getKey(), new SecretKeySpec(value, "AES"));
        }
        if (!decoded.containsKey(currentVersion)) throw new IllegalStateException("Current credential key is unavailable");
        this.currentVersion = currentVersion;
        this.keys = Collections.unmodifiableMap(decoded);
        this.random = random;
    }

    public EncryptedCredential encrypt(String plaintext) {
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        return encryptWith(currentVersion, plaintext, iv);
    }

    public String decrypt(String ciphertext, String iv, String keyVersion) {
        SecretKeySpec key = keys.get(keyVersion);
        if (key == null) throw new IllegalStateException("Credential key version is unavailable");
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.getDecoder().decode(iv)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(ciphertext)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("Credential decryption failed");
        }
    }

    public String getCurrentVersion() { return currentVersion; }

    private EncryptedCredential encryptWith(String version, String plaintext, byte[] iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keys.get(version), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new EncryptedCredential(Base64.getEncoder().encodeToString(encrypted),
                Base64.getEncoder().encodeToString(iv), version);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Credential encryption failed");
        }
    }
}
