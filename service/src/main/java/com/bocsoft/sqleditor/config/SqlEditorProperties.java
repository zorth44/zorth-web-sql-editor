package com.bocsoft.sqleditor.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sql-editor")
public class SqlEditorProperties {
    @Valid private final Auth auth = new Auth();
    @Valid private final Credentials credentials = new Credentials();
    @Valid private final Cursor cursor = new Cursor();
    @Valid private final Network network = new Network();
    @Valid private final Pools pools = new Pools();

    public Auth getAuth() { return auth; }
    public Credentials getCredentials() { return credentials; }
    public Cursor getCursor() { return cursor; }
    public Network getNetwork() { return network; }
    public Pools getPools() { return pools; }

    @PostConstruct
    public void validateConfiguration() {
        if (credentials.keys.isEmpty() || !credentials.keys.containsKey(credentials.currentVersion)) {
            throw new IllegalStateException("A current credential key version must be configured");
        }
        if (network.allowedCidrs.isEmpty()) {
            throw new IllegalStateException("At least one target-network allowed CIDR is required");
        }
        if (auth.cacheTtlSeconds > 60) {
            throw new IllegalStateException("Authorization cache TTL cannot exceed 60 seconds");
        }
        if (pools.maxPools * pools.maxPoolSize > pools.maxConnections) {
            throw new IllegalStateException("Pool limits exceed the global target connection limit");
        }
        validateKeyMaterial(credentials.keys, "Credential key", true);
        validateSingleKey(cursor.signingKey, "Cursor signing key");
        validateCidrs(network.allowedCidrs);
        validateCidrs(network.deniedCidrs);
    }

    private void validateKeyMaterial(Map<String,String> values,String label,boolean exactly32){for(Map.Entry<String,String> entry:values.entrySet()){byte[] decoded;try{decoded=java.util.Base64.getDecoder().decode(entry.getValue());}catch(IllegalArgumentException exception){throw new IllegalStateException(label+" is not valid Base64");}if(exactly32&&decoded.length!=32)throw new IllegalStateException(label+" must be exactly 256 bits");}}
    private void validateSingleKey(String value,String label){if(value==null||value.trim().isEmpty())return;byte[] decoded;try{decoded=java.util.Base64.getDecoder().decode(value);}catch(IllegalArgumentException exception){throw new IllegalStateException(label+" is not valid Base64");}if(decoded.length<32)throw new IllegalStateException(label+" must be at least 256 bits");}
    private void validateCidrs(List<String> values){for(String value:values){try{parseCidr(value);}catch(RuntimeException exception){throw new IllegalStateException("Invalid target-network CIDR configuration");}}}
    private void parseCidr(String value){if(value==null)throw new IllegalArgumentException();String[] parts=value.trim().split("/",-1);if(parts.length!=2)throw new IllegalArgumentException();try{java.net.InetAddress address=java.net.InetAddress.getByName(parts[0]);int prefix=Integer.parseInt(parts[1]);if(prefix<0||prefix>address.getAddress().length*8)throw new IllegalArgumentException();}catch(java.net.UnknownHostException exception){throw new IllegalArgumentException(exception);}}

    public static class Auth {
        @NotBlank private String contextUrl;
        @NotBlank private String internalServiceKey;
        @Min(100) @Max(30000) private int connectTimeoutMs = 2000;
        @Min(100) @Max(30000) private int readTimeoutMs = 3000;
        @Min(0) @Max(60) private int cacheTtlSeconds = 60;
        @Min(1) private long cacheMaximumSize = 10000;
        public String getContextUrl() { return contextUrl; }
        public void setContextUrl(String contextUrl) { this.contextUrl = contextUrl; }
        public String getInternalServiceKey() { return internalServiceKey; }
        public void setInternalServiceKey(String internalServiceKey) { this.internalServiceKey = internalServiceKey; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
        public int getCacheTtlSeconds() { return cacheTtlSeconds; }
        public void setCacheTtlSeconds(int cacheTtlSeconds) { this.cacheTtlSeconds = cacheTtlSeconds; }
        public long getCacheMaximumSize() { return cacheMaximumSize; }
        public void setCacheMaximumSize(long cacheMaximumSize) { this.cacheMaximumSize = cacheMaximumSize; }
    }

    public static class Credentials {
        @NotBlank private String currentVersion;
        private Map<String, String> keys = new LinkedHashMap<String, String>();
        @Valid private final Rotation rotation = new Rotation();
        public String getCurrentVersion() { return currentVersion; }
        public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
        public Map<String, String> getKeys() { return keys; }
        public void setKeys(Map<String, String> keys) { this.keys = keys; }
        public Rotation getRotation() { return rotation; }
    }

    public static class Rotation {
        private boolean enabled;
        private boolean dryRun = true;
        private String fromVersion;
        @Min(1) @Max(1000) private int batchSize = 100;
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isDryRun() { return dryRun; }
        public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
        public String getFromVersion() { return fromVersion; }
        public void setFromVersion(String fromVersion) { this.fromVersion = fromVersion; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Cursor {
        @NotBlank private String signingKey;
        @Min(128) @Max(8192) private int maximumTokenLength = 2048;
        public String getSigningKey() { return signingKey; }
        public void setSigningKey(String signingKey) { this.signingKey = signingKey; }
        public int getMaximumTokenLength() { return maximumTokenLength; }
        public void setMaximumTokenLength(int maximumTokenLength) { this.maximumTokenLength = maximumTokenLength; }
    }

    public static class Network {
        private List<String> allowedCidrs = new ArrayList<String>();
        private List<String> deniedCidrs = new ArrayList<String>();
        public List<String> getAllowedCidrs() { return allowedCidrs; }
        public void setAllowedCidrs(List<String> allowedCidrs) { this.allowedCidrs = allowedCidrs; }
        public List<String> getDeniedCidrs() { return deniedCidrs; }
        public void setDeniedCidrs(List<String> deniedCidrs) { this.deniedCidrs = deniedCidrs; }
    }

    public static class Pools {
        @Min(1) private int maxPools = 10;
        @Min(1) private int maxConnections = 50;
        @Min(1) @Max(5) private int maxPoolSize = 5;
        @Min(1) private int idlePoolMinutes = 30;
        public int getMaxPools() { return maxPools; }
        public void setMaxPools(int maxPools) { this.maxPools = maxPools; }
        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public void setMaxPoolSize(int maxPoolSize) { this.maxPoolSize = maxPoolSize; }
        public int getIdlePoolMinutes() { return idlePoolMinutes; }
        public void setIdlePoolMinutes(int idlePoolMinutes) { this.idlePoolMinutes = idlePoolMinutes; }
    }
}
