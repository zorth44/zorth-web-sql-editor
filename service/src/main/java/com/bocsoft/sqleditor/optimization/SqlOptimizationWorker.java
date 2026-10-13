package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.AuthClient;
import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.bocsoft.sqleditor.optimization.api.OptimizationReport;
import com.bocsoft.sqleditor.optimization.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.*;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class SqlOptimizationWorker {
    private final OptimizationRepository repository;private final OptimizationProperties properties;
    private final TaskCredentialVault vault;private final AuthClient authClient;private final DataSourceService sources;
    private final SqlLiteAgentClient agent;private final OptimizationReportValidator reports;private final ObjectMapper json;private final Clock clock;private final MeterRegistry metrics;
    private final ScheduledExecutorService heartbeats=Executors.newScheduledThreadPool(1,r->{Thread t=new Thread(r,"optimization-heartbeat");t.setDaemon(true);return t;});
    public SqlOptimizationWorker(OptimizationRepository repository,OptimizationProperties properties,TaskCredentialVault vault,AuthClient authClient,DataSourceService sources,SqlLiteAgentClient agent,OptimizationReportValidator reports,ObjectMapper json,Clock clock,MeterRegistry metrics){
        this.repository=repository;this.properties=properties;this.vault=vault;this.authClient=authClient;this.sources=sources;this.agent=agent;this.reports=reports;this.json=json;this.clock=clock;this.metrics=metrics;
    }
    public void execute(OptimizationAttempt attempt){
        long start=System.nanoTime();String code=null;String report=null;com.bocsoft.sqleditor.optimization.api.TaskSummary summary=null;
        ScheduledFuture<?> heartbeat=heartbeats.scheduleAtFixedRate(()->{
            try{repository.heartbeat(attempt,clock.instant(),properties.getLeaseSeconds());}catch(Exception ignored){metrics.counter("sql.optimization.heartbeat.errors").increment();}
        },properties.getHeartbeatSeconds(),properties.getHeartbeatSeconds(),TimeUnit.SECONDS);
        try{
            OptimizationCredential credential=repository.credential(attempt.task.id);
            if(credential==null)throw new OptimizationFailure("CREDENTIAL_UNAVAILABLE");
            if(!credential.expiresAt.isAfter(clock.instant().plusSeconds(properties.getAttemptBudgetSeconds())))throw new OptimizationFailure("CREDENTIAL_EXPIRED");
            String token=vault.decrypt(attempt.task.id,credential.ciphertext,credential.version);
            AuthContext auth;
            try{auth=authClient.resolve(token);}catch(ApiException e){throw new OptimizationFailure("UNAUTHENTICATED".equals(e.getCode())?"CREDENTIAL_EXPIRED":("USER_PRODUCT_CONTEXT_INVALID".equals(e.getCode())?"IDENTITY_CHANGED":"CREDENTIAL_UNAVAILABLE"));}
            if(!auth.getUserId().equals(attempt.task.userId)||!auth.getProductId().equals(attempt.task.productId))throw new OptimizationFailure("IDENTITY_CHANGED");
            if(!auth.getTokenExpiresAt().isAfter(clock.instant().plusSeconds(properties.getAttemptBudgetSeconds())))throw new OptimizationFailure("CREDENTIAL_EXPIRED");
            if(!properties.permits(auth))throw new OptimizationFailure("CREDENTIAL_UNAVAILABLE");
            try{if(!attempt.task.engineId.equals(sources.get(auth,attempt.task.datasourceId).getEngine()))throw new OptimizationFailure("DATA_SOURCE_UNAVAILABLE");}catch(ApiException e){throw new OptimizationFailure("DATA_SOURCE_UNAVAILABLE");}
            if(!clock.instant().isBefore(attempt.deadline)||!repository.heartbeat(attempt,clock.instant(),properties.getLeaseSeconds()))throw new OptimizationFailure("WORKER_LOST");
            OptimizationReport result=agent.analyze(attempt.task.datasourceId,attempt.task.database,attempt.task.sql,attempt.sessionId,attempt.requestId,token,auth);
            byte[] bytes=json.writeValueAsBytes(result);if(bytes.length>properties.getMaxReportBytes())throw new OptimizationFailure("REPORT_TOO_LARGE");
            report=new String(bytes,java.nio.charset.StandardCharsets.UTF_8);summary=reports.summary(result);
        }catch(OptimizationFailure e){code=e.getCode();}catch(Exception e){code="INTERNAL_ERROR";}
        finally{heartbeat.cancel(false);}
        boolean changed=repository.finish(attempt,clock.instant(),report,summary,code);
        if(changed){
            metrics.counter("sql.optimization.completed","outcome",code==null?"SUCCEEDED":code).increment();
            metrics.timer("sql.optimization.duration").record(System.nanoTime()-start,TimeUnit.NANOSECONDS);
            if(summary!=null)metrics.counter("sql.optimization.limitations").increment(summary.getLimitationCount());
        }else metrics.counter("sql.optimization.late.results").increment();
    }
    @PreDestroy public void close(){heartbeats.shutdownNow();}
}
