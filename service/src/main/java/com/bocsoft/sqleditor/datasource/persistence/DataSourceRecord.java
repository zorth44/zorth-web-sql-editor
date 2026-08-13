package com.bocsoft.sqleditor.datasource.persistence;

import java.time.Instant;

public class DataSourceRecord {
    private String id;
    private String productId;
    private String name;
    private String engine;
    private String host;
    private int port;
    private String username;
    private String passwordCiphertext;
    private String passwordIv;
    private String keyVersion;
    private String defaultDatabase;
    private String sslMode;
    private int connectTimeoutSeconds;
    private String propertiesJson;
    private String description;
    private String lastTestStatus;
    private Instant lastTestAt;
    private String lastTestMessage;
    private long version;
    private String createdBy;
    private String createdByName;
    private Instant createdAt;
    private String updatedBy;
    private String updatedByName;
    private Instant updatedAt;

    public String getId() { return id; } public void setId(String v) { id=v; }
    public String getProductId() { return productId; } public void setProductId(String v) { productId=v; }
    public String getName() { return name; } public void setName(String v) { name=v; }
    public String getEngine() { return engine; } public void setEngine(String v) { engine=v; }
    public String getHost() { return host; } public void setHost(String v) { host=v; }
    public int getPort() { return port; } public void setPort(int v) { port=v; }
    public String getUsername() { return username; } public void setUsername(String v) { username=v; }
    public String getPasswordCiphertext() { return passwordCiphertext; } public void setPasswordCiphertext(String v) { passwordCiphertext=v; }
    public String getPasswordIv() { return passwordIv; } public void setPasswordIv(String v) { passwordIv=v; }
    public String getKeyVersion() { return keyVersion; } public void setKeyVersion(String v) { keyVersion=v; }
    public String getDefaultDatabase() { return defaultDatabase; } public void setDefaultDatabase(String v) { defaultDatabase=v; }
    public String getSslMode() { return sslMode; } public void setSslMode(String v) { sslMode=v; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; } public void setConnectTimeoutSeconds(int v) { connectTimeoutSeconds=v; }
    public String getPropertiesJson() { return propertiesJson; } public void setPropertiesJson(String v) { propertiesJson=v; }
    public String getDescription() { return description; } public void setDescription(String v) { description=v; }
    public String getLastTestStatus() { return lastTestStatus; } public void setLastTestStatus(String v) { lastTestStatus=v; }
    public Instant getLastTestAt() { return lastTestAt; } public void setLastTestAt(Instant v) { lastTestAt=v; }
    public String getLastTestMessage() { return lastTestMessage; } public void setLastTestMessage(String v) { lastTestMessage=v; }
    public long getVersion() { return version; } public void setVersion(long v) { version=v; }
    public String getCreatedBy() { return createdBy; } public void setCreatedBy(String v) { createdBy=v; }
    public String getCreatedByName() { return createdByName; } public void setCreatedByName(String v) { createdByName=v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt=v; }
    public String getUpdatedBy() { return updatedBy; } public void setUpdatedBy(String v) { updatedBy=v; }
    public String getUpdatedByName() { return updatedByName; } public void setUpdatedByName(String v) { updatedByName=v; }
    public Instant getUpdatedAt() { return updatedAt; } public void setUpdatedAt(Instant v) { updatedAt=v; }
}
