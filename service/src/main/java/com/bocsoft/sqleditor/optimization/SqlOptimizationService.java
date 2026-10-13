package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.bocsoft.sqleditor.datasource.api.DataSourceDetailResponse;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.execution.SqlSafetyEvaluator;
import com.bocsoft.sqleditor.optimization.api.*;
import com.bocsoft.sqleditor.optimization.persistence.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SqlOptimizationService {
    private final OptimizationRepository repository;
    private final OptimizationProperties properties;
    private final DataSourceService sources;
    private final EngineRegistry engines;
    private final SqlSafetyEvaluator safety;
    private final TaskCredentialVault vault;
    private final OptimizationCursorCodec cursors;
    private final ObjectMapper json;
    private final Clock clock;
    public SqlOptimizationService(OptimizationRepository repository, OptimizationProperties properties, DataSourceService sources,
            EngineRegistry engines, SqlSafetyEvaluator safety, TaskCredentialVault vault, OptimizationCursorCodec cursors, ObjectMapper json, Clock clock) {
        this.repository=repository;this.properties=properties;this.sources=sources;this.engines=engines;this.safety=safety;
        this.vault=vault;this.cursors=cursors;this.json=json;this.clock=clock;
    }
    public AcceptedTask create(AuthContext auth, CreateTaskRequest input, String key, String token, String requestId) {
        properties.require(auth); key=key(key); CreateTaskRequest normalized=normalize(input);
        DataSourceDetailResponse source=sources.get(auth,normalized.getDatasourceId());
        return submit(auth,normalized,source,key,token,requestId,null,"create");
    }
    public AcceptedTask retry(AuthContext auth,String id,String key,String token,String requestId) {
        properties.require(auth);key=key(key);OptimizationTask original=require(auth,id);
        CreateTaskRequest input=new CreateTaskRequest();input.setName(original.name);input.setDatasourceId(original.datasourceId);input.setDatabase(original.database);input.setSql(original.sql);
        DataSourceDetailResponse source=sources.get(auth,input.getDatasourceId());
        // Existing replay is checked before the source's current terminal eligibility or quota.
        return submit(auth,input,source,key,token,requestId,original,"retry");
    }
    private AcceptedTask submit(AuthContext auth,CreateTaskRequest input,DataSourceDetailResponse source,String key,String token,String requestId,OptimizationTask original,String operation) {
        String fingerprint=fingerprint(operation,original==null?null:original.id,input);
        return repository.transaction(() -> {
            repository.lockCreation();
            OptimizationTask prior=repository.byKey(auth.getUserId(),auth.getProductId(),key);
            if(prior!=null){
                if(!fingerprint.equals(prior.fingerprint))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT");
                require(auth,prior.id);return accepted(prior);
            }
            validate(input,source);
            if(original!=null && original.status!=TaskStatus.SUCCEEDED && original.status!=TaskStatus.FAILED)throw error(HttpStatus.CONFLICT,"TASK_NOT_TERMINAL");
            if(repository.unfinished(auth.getUserId(),auth.getProductId())>=properties.getUserQuota() || repository.unfinished(null,null)>=properties.getGlobalQuota())throw error(HttpStatus.TOO_MANY_REQUESTS,"OPTIMIZATION_QUOTA_EXCEEDED");
            Instant now=clock.instant().truncatedTo(ChronoUnit.MICROS);
            Instant expiry=now.plusSeconds(properties.getCredentialTtlSeconds());
            if(auth.getTokenExpiresAt()==null)throw ApiException.unauthenticated();
            if(expiry.isAfter(auth.getTokenExpiresAt()))expiry=auth.getTokenExpiresAt();
            if(!expiry.isAfter(now.plusSeconds(properties.getAttemptBudgetSeconds())))throw ApiException.validation("authorization","CREDENTIAL_EXPIRED","凭证即将到期，请重新登录后提交");
            if(token==null || token.isEmpty())throw ApiException.unauthenticated();
            OptimizationTask task=new OptimizationTask();task.id=UUID.randomUUID().toString();task.userId=auth.getUserId();task.productId=auth.getProductId();
            task.name=input.getName().isEmpty()?"SQL scan "+task.id.substring(0,8):input.getName();task.datasourceId=input.getDatasourceId();task.datasourceName=source.getName();task.engineId=source.getEngine();
            task.database=input.getDatabase();task.sql=input.getSql();task.idempotencyKey=key;task.fingerprint=fingerprint;task.sourceTaskId=original==null?null:original.id;
            task.status=TaskStatus.QUEUED;task.createdAt=now;task.expiresAt=now.plus(properties.getRetentionDays(),ChronoUnit.DAYS);task.requestId=requestId;
            OptimizationCredential credential=new OptimizationCredential();credential.version=properties.getCurrentKeyVersion();credential.expiresAt=expiry;
            try {credential.ciphertext=vault.encrypt(task.id,token,credential.version);}catch(OptimizationFailure failure){throw error(HttpStatus.SERVICE_UNAVAILABLE,failure.getCode());}
            repository.insert(task,credential);return accepted(task);
        });
    }
    public TaskDetail detail(AuthContext auth,String id){properties.require(auth);return detail(require(auth,id));}
    public TaskListResponse list(AuthContext auth,String status,int pageSize,String pageToken){
        properties.require(auth);
        if(pageSize<1||pageSize>100)throw ApiException.validation("pageSize","OUT_OF_RANGE","每页数量为 1 到 100");
        if(status!=null){try{TaskStatus.valueOf(status);}catch(Exception e){throw ApiException.validation("status","INVALID","状态无效");}}
        OptimizationCursorCodec.Position position=pageToken==null?null:cursors.decode(pageToken,auth,status,pageSize);
        List<OptimizationTask> rows=repository.list(auth.getUserId(),auth.getProductId(),status,position==null?null:position.at,position==null?null:position.id,pageSize+1);
        List<TaskListItem> items=new ArrayList<TaskListItem>();
        for(int i=0;i<Math.min(rows.size(),pageSize);i++)items.add(listItem(rows.get(i)));
        String next=null;if(rows.size()>pageSize){OptimizationTask last=rows.get(pageSize-1);next=cursors.encode(auth,status,pageSize,last.createdAt,last.id);}
        TaskListResponse result=new TaskListResponse();result.setItems(items);result.setNextPageToken(next);return result;
    }
    private OptimizationTask require(AuthContext auth,String id){
        OptimizationTask task=repository.visible(id,auth.getUserId(),auth.getProductId());
        if(task==null)throw error(HttpStatus.NOT_FOUND,"OPTIMIZATION_TASK_NOT_FOUND");
        try{sources.get(auth,task.datasourceId);}catch(ApiException e){if(e.getStatus()==HttpStatus.NOT_FOUND)throw error(HttpStatus.NOT_FOUND,"OPTIMIZATION_TASK_NOT_FOUND");throw e;}
        return task;
    }
    private void validate(CreateTaskRequest input,DataSourceDetailResponse source){
        EngineSupport engine=engines.requireSaved(source.getEngine());
        try{OptimizationSqlGuard.requireReadOnly(input.getSql(),engine.id());engine.validateIdentifier("database",input.getDatabase());safety.requireSafeSelect(engine,input.getSql());}
        catch(ApiException e){throw ApiException.validation("sql","INVALID_SQL","仅支持当前引擎的一条只读 SELECT SQL 和有效命名空间");}
        SqlLiteAgentClient.message(input.getDatasourceId(),input.getDatabase(),input.getSql());
    }
    private CreateTaskRequest normalize(CreateTaskRequest input){
        if(input==null)throw ApiException.validation("body","REQUIRED","请求不能为空");
        CreateTaskRequest result=new CreateTaskRequest();String name=input.getName()==null?"":input.getName().trim();
        if(name.length()>100)throw ApiException.validation("name","OUT_OF_RANGE","名称最多 100 字符");result.setName(name);
        result.setDatasourceId(coordinate("datasourceId",input.getDatasourceId(),36));result.setDatabase(coordinate("database",input.getDatabase(),256));
        if(input.getSql()==null||input.getSql().trim().isEmpty()||input.getSql().length()>20000)throw ApiException.validation("sql","OUT_OF_RANGE","SQL 长度须为 1 到 20000 字符");
        result.setSql(input.getSql());return result;
    }
    private String coordinate(String field,String value,int max){
        if(value==null||value.trim().isEmpty()||value.length()>max||value.codePoints().anyMatch(Character::isISOControl))throw ApiException.validation(field,"INVALID","坐标不能为空或包含控制字符");return value.trim();
    }
    private String key(String key){try{String canonical=UUID.fromString(key).toString();if(!canonical.equalsIgnoreCase(key))throw new IllegalArgumentException();return canonical;}
        catch(Exception e){throw ApiException.validation("Idempotency-Key","INVALID","幂等键必须是 UUID");}}
    private String fingerprint(String operation,String original,CreateTaskRequest input){try{return OptimizationDigests.sha256(json.writeValueAsString(Arrays.asList(operation,original,input.getName(),input.getDatasourceId(),input.getDatabase(),input.getSql())));}catch(Exception e){throw new IllegalStateException("Cannot fingerprint task");}}
    private AcceptedTask accepted(OptimizationTask task){AcceptedTask result=new AcceptedTask();result.setId(task.id);result.setStatus(task.status);result.setCreatedAt(task.createdAt);return result;}
    private TaskSummary summary(OptimizationTask task){if(task.status!=TaskStatus.SUCCEEDED)return null;TaskSummary s=new TaskSummary();s.setPolicyIssueCount(task.policyCount);s.setPerformanceIssueCount(task.performanceCount);s.setLimitationCount(task.limitationCount);return s;}
    private TaskListItem listItem(OptimizationTask t){
        TaskListItem r=new TaskListItem();r.setId(t.id);r.setName(t.name);r.setDatasourceId(t.datasourceId);r.setDatasourceName(t.datasourceName);r.setDatabase(t.database);
        String preview=t.sql.replace('\r',' ').replace('\n',' ');r.setSqlPreview(preview.substring(0,Math.min(160,preview.length())));r.setStatus(t.status);r.setCreatedAt(t.createdAt);r.setStartedAt(t.startedAt);r.setCompletedAt(t.completedAt);r.setSummary(summary(t));return r;
    }
    private TaskDetail detail(OptimizationTask t){
        TaskDetail r=new TaskDetail();r.setId(t.id);r.setName(t.name);r.setDatasourceId(t.datasourceId);r.setDatasourceName(t.datasourceName);r.setDatabase(t.database);r.setSql(t.sql);r.setStatus(t.status);
        r.setCreatedAt(t.createdAt);r.setStartedAt(t.startedAt);r.setCompletedAt(t.completedAt);r.setSourceTaskId(t.sourceTaskId);r.setSummary(summary(t));
        if(t.status==TaskStatus.SUCCEEDED){r.setReportSchemaVersion(t.schemaVersion);try{r.setReport(json.readValue(t.reportJson,OptimizationReport.class));}catch(Exception e){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"INTERNAL_ERROR");}}
        if(t.status==TaskStatus.FAILED){TaskError e=new TaskError();e.setCode(t.errorCode);e.setMessage(t.errorMessage);e.setRequestId(t.requestId);r.setError(e);}return r;
    }
    private ApiException error(HttpStatus status,String code){return new ApiException(status,code,"SQL 扫描请求未完成");}
}
