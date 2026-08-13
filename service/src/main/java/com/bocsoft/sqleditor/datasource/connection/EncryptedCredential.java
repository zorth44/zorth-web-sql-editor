package com.bocsoft.sqleditor.datasource.connection;

public final class EncryptedCredential {
    private final String ciphertext;
    private final String iv;
    private final String keyVersion;
    public EncryptedCredential(String ciphertext, String iv, String keyVersion) {
        this.ciphertext = ciphertext;
        this.iv = iv;
        this.keyVersion = keyVersion;
    }
    public String getCiphertext() { return ciphertext; }
    public String getIv() { return iv; }
    public String getKeyVersion() { return keyVersion; }
}
