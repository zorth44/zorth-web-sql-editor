package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;

@Component
@ConfigurationProperties(prefix = "sql-editor.optimization")
public class OptimizationProperties {
    private boolean enabled = false;
    private boolean integrationReady = false;
    private java.util.Set<String> allowedUsers = new java.util.HashSet<String>();
    private java.util.Set<String> allowedProducts = new java.util.HashSet<String>();
    private String agentBaseUrl = "http://127.0.0.1:18090";
    private boolean developmentUserHeader = false;
    private int connectTimeoutMs = 3000;
    private int callTimeoutMs = 135000;
    private int attemptBudgetSeconds = 150;
    private int leaseSeconds = 180;
    private int heartbeatSeconds = 15;
    private int workerConcurrency = 2;
    private boolean schedulerEnabled = true;
    private int userQuota = 10;
    private int globalQuota = 100;
    private int credentialTtlSeconds = 600;
    private int retentionDays = 90;
    private int maxReportBytes = 1048576;
    private String currentKeyVersion = "v1";
    private java.util.Map<String,String> credentialKeys = new java.util.HashMap<String,String>();
    private String cursorKey = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isIntegrationReady() { return integrationReady; }
    public void setIntegrationReady(boolean value) { integrationReady = value; }
    public java.util.Set<String> getAllowedUsers() { return allowedUsers; }
    public void setAllowedUsers(java.util.Set<String> value) { allowedUsers = value; }
    public java.util.Set<String> getAllowedProducts() { return allowedProducts; }
    public void setAllowedProducts(java.util.Set<String> value) { allowedProducts = value; }
    public String getAgentBaseUrl() { return agentBaseUrl; }
    public void setAgentBaseUrl(String value) { agentBaseUrl = value; }
    public boolean isDevelopmentUserHeader() { return developmentUserHeader; }
    public void setDevelopmentUserHeader(boolean value) { developmentUserHeader = value; }
    public int getConnectTimeoutMs() { return connectTimeoutMs; }
    public void setConnectTimeoutMs(int value) { connectTimeoutMs = value; }
    public int getCallTimeoutMs() { return callTimeoutMs; }
    public void setCallTimeoutMs(int value) { callTimeoutMs = value; }
    public int getAttemptBudgetSeconds() { return attemptBudgetSeconds; }
    public void setAttemptBudgetSeconds(int value) { attemptBudgetSeconds = value; }
    public int getLeaseSeconds() { return leaseSeconds; }
    public void setLeaseSeconds(int value) { leaseSeconds = value; }
    public int getHeartbeatSeconds() { return heartbeatSeconds; }
    public void setHeartbeatSeconds(int value) { heartbeatSeconds = value; }
    public int getWorkerConcurrency() { return workerConcurrency; }
    public void setWorkerConcurrency(int value) { workerConcurrency = value; }
    public boolean isSchedulerEnabled() { return schedulerEnabled; }
    public void setSchedulerEnabled(boolean value) { schedulerEnabled = value; }
    public int getUserQuota() { return userQuota; }
    public void setUserQuota(int value) { userQuota = value; }
    public int getGlobalQuota() { return globalQuota; }
    public void setGlobalQuota(int value) { globalQuota = value; }
    public int getCredentialTtlSeconds() { return credentialTtlSeconds; }
    public void setCredentialTtlSeconds(int value) { credentialTtlSeconds = value; }
    public int getRetentionDays() { return retentionDays; }
    public void setRetentionDays(int value) { retentionDays = value; }
    public int getMaxReportBytes() { return maxReportBytes; }
    public void setMaxReportBytes(int value) { maxReportBytes = value; }
    public String getCurrentKeyVersion() { return currentKeyVersion; }
    public void setCurrentKeyVersion(String value) { currentKeyVersion = value; }
    public java.util.Map<String,String> getCredentialKeys() { return credentialKeys; }
    public void setCredentialKeys(java.util.Map<String,String> value) { credentialKeys = value; }
    public String getCursorKey() { return cursorKey; }
    public void setCursorKey(String value) { cursorKey = value; }

    public boolean permits(AuthContext auth) {
        return enabled && integrationReady && allowedUsers.contains(auth.getUserId()) && allowedProducts.contains(auth.getProductId());
    }
    public void require(AuthContext auth) {
        if (!permits(auth)) throw new ApiException(HttpStatus.FORBIDDEN, "SQL_OPTIMIZE_FORBIDDEN", "当前用户未开放 SQL 优化能力");
    }
}
