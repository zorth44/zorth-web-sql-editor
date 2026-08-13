package com.bocsoft.sqleditor.datasource.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

public class ConnectionRequest {
    @NotBlank @Size(max=255) private String host;
    @NotNull @Min(1) @Max(65535) private Integer port;
    @NotBlank @Size(max=128) private String username;
    @Size(max=1024) @Schema(accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    @Size(max=64) private String defaultDatabase;
    @NotBlank private String sslMode;
    @NotNull @Min(1) @Max(30) private Integer connectTimeoutSeconds;
    @NotNull private Map<String, String> properties = new LinkedHashMap<String, String>();
    public String getHost() { return host; } public void setHost(String v) { host=v; }
    public Integer getPort() { return port; } public void setPort(Integer v) { port=v; }
    public String getUsername() { return username; } public void setUsername(String v) { username=v; }
    public String getPassword() { return password; } public void setPassword(String v) { password=v; }
    public String getDefaultDatabase() { return defaultDatabase; } public void setDefaultDatabase(String v) { defaultDatabase=v; }
    public String getSslMode() { return sslMode; } public void setSslMode(String v) { sslMode=v; }
    public Integer getConnectTimeoutSeconds() { return connectTimeoutSeconds; } public void setConnectTimeoutSeconds(Integer v) { connectTimeoutSeconds=v; }
    public Map<String, String> getProperties() { return properties; } public void setProperties(Map<String, String> v) { properties=v; }
}
