package com.bocsoft.sqleditor.optimization;

import java.net.URI;
import java.util.Base64;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
public class OptimizationConfiguration {
    private final OptimizationProperties p;
    private final Environment environment;
    public OptimizationConfiguration(OptimizationProperties p,Environment environment){this.p=p;this.environment=environment;}
    @PostConstruct public void validate(){
        if(!p.isEnabled())return;
        if(!p.isIntegrationReady() || p.getAllowedUsers().isEmpty() || p.getAllowedProducts().isEmpty())throw new IllegalStateException("Optimization requires verified integration and explicit user/product allowlists");
        try {
            if(Base64.getDecoder().decode(p.getCredentialKeys().get(p.getCurrentKeyVersion())).length!=32 || Base64.getDecoder().decode(p.getCursorKey()).length<32)throw new IllegalArgumentException();
            for(String key:p.getCredentialKeys().values()) {
                if(Base64.getDecoder().decode(key).length!=32)throw new IllegalArgumentException();
                String databaseKey=environment.getProperty("sql-editor.credentials.keys."+environment.getProperty("sql-editor.credentials.current-version","v1"));
                if(databaseKey!=null && java.security.MessageDigest.isEqual(Base64.getDecoder().decode(key),Base64.getDecoder().decode(databaseKey)))throw new IllegalArgumentException();
            }
            URI uri=URI.create(p.getAgentBaseUrl());
            if(uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null || uri.getFragment()!=null || (!"http".equals(uri.getScheme())&&!"https".equals(uri.getScheme())))throw new IllegalArgumentException();
        }catch(Exception e){throw new IllegalStateException("Optimization requires independent configured encryption/cursor keys and valid Agent URL");}
        if(p.getConnectTimeoutMs()<1 || p.getCallTimeoutMs()<1 || p.getWorkerConcurrency()<1 || p.getUserQuota()<1 || p.getGlobalQuota()<1 || p.getRetentionDays()<1 || p.getMaxReportBytes()<1
            || p.getAttemptBudgetSeconds()*1000L<=p.getCallTimeoutMs() || p.getCredentialTtlSeconds()<=p.getAttemptBudgetSeconds()
            || p.getLeaseSeconds()<=p.getHeartbeatSeconds() || p.getHeartbeatSeconds()<1)throw new IllegalStateException("Invalid optimization budgets");
        if(p.isDevelopmentUserHeader() && !environment.acceptsProfiles(Profiles.of("dev","test","local-optimization")))throw new IllegalStateException("Development Agent identity header is prohibited outside local/test profiles");
    }
}
