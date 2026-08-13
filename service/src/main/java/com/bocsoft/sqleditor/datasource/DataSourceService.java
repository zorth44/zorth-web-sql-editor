package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.datasource.api.ConnectionRequest;
import com.bocsoft.sqleditor.datasource.api.ConnectionTestResult;
import com.bocsoft.sqleditor.datasource.api.CreateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.api.DataSourceDetailResponse;
import com.bocsoft.sqleditor.datasource.api.DataSourceListItemResponse;
import com.bocsoft.sqleditor.datasource.api.UpdateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.CredentialCipher;
import com.bocsoft.sqleditor.datasource.connection.EncryptedCredential;
import com.bocsoft.sqleditor.datasource.connection.ShortLivedConnectionTester;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceMapper;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class DataSourceService {
    private final DataSourceMapper mapper; private final DataSourceValidator validator;
    private final DataSourceResponseMapper responses; private final CredentialCipher cipher;
    private final CursorCodec cursors; private final ObjectMapper objectMapper; private final Clock clock;
    private final PoolLifecycle pools; private final RunningTaskCounter runningTasks;
    private final ShortLivedConnectionTester connectionTester;
    private final SqlEditorMetrics metrics;

    public DataSourceService(DataSourceMapper mapper, DataSourceValidator validator,
                             DataSourceResponseMapper responses, CredentialCipher cipher,
                             CursorCodec cursors, ObjectMapper objectMapper, Clock clock,
                             PoolLifecycle pools, RunningTaskCounter runningTasks,
                             ShortLivedConnectionTester connectionTester, SqlEditorMetrics metrics) {
        this.mapper=mapper; this.validator=validator; this.responses=responses; this.cipher=cipher;
        this.cursors=cursors; this.objectMapper=objectMapper; this.clock=clock; this.pools=pools;
        this.runningTasks=runningTasks; this.connectionTester=connectionTester;
        this.metrics=metrics;
    }

    @Transactional(readOnly=true)
    public CursorPage<DataSourceListItemResponse> list(AuthContext auth, String keyword,
                                                       int pageSize, String pageToken) {
        String normalized=CursorCodec.normalize(keyword);
        if (normalized.length()>100) throw ApiException.validation("keyword", "OUT_OF_RANGE", "关键词最多 100 个字符");
        if (pageSize<1 || pageSize>100) throw ApiException.validation("pageSize", "OUT_OF_RANGE", "每页数量必须在 1 到 100 之间");
        CursorPosition cursor=pageToken==null || pageToken.isEmpty() ? null : cursors.decode(pageToken, normalized, pageSize);
        String pattern=normalized.isEmpty()?null:"%"+escapeLike(normalized)+"%";
        List<DataSourceRecord> records=mapper.list(auth.getProductId(), pattern,
            cursor==null?null:cursor.getUpdatedAt(), cursor==null?null:cursor.getId(), pageSize+1);
        boolean more=records.size()>pageSize;
        List<DataSourceListItemResponse> items=new ArrayList<DataSourceListItemResponse>();
        int size=Math.min(records.size(),pageSize);
        for(int i=0;i<size;i++) items.add(responses.listItem(records.get(i)));
        String next=null;
        if(more && size>0){ DataSourceRecord last=records.get(size-1); next=cursors.encode(last.getUpdatedAt(),last.getId(),normalized,pageSize); }
        metrics.dataSource("list","success"); return new CursorPage<DataSourceListItemResponse>(items,next);
    }

    @Transactional(readOnly=true)
    public DataSourceDetailResponse get(AuthContext auth,String id){ DataSourceDetailResponse result=responses.detail(require(auth,id));metrics.dataSource("detail","success");return result; }

    @Transactional
    public DataSourceDetailResponse create(AuthContext auth,CreateDataSourceRequest request){
        validator.validateCreate(request);
        Instant now=clock.instant(); EncryptedCredential secret=cipher.encrypt(request.getPassword());
        DataSourceRecord record=new DataSourceRecord(); record.setId(UUID.randomUUID().toString());
        record.setProductId(auth.getProductId()); apply(record,request,secret);
        record.setVersion(1); record.setCreatedBy(auth.getUserId()); record.setCreatedByName(auth.getDisplayName());
        record.setCreatedAt(now); record.setUpdatedBy(auth.getUserId()); record.setUpdatedByName(auth.getDisplayName()); record.setUpdatedAt(now);
        mapper.insert(record); metrics.dataSource("create","success"); return responses.detail(record);
    }

    @Transactional
    public DataSourceDetailResponse update(AuthContext auth,String id,UpdateDataSourceRequest request){
        validator.validateUpdate(request); DataSourceRecord current=require(auth,id);
        EncryptedCredential secret;
        if(request.getPassword()==null || request.getPassword().isEmpty()) secret=new EncryptedCredential(current.getPasswordCiphertext(),current.getPasswordIv(),current.getKeyVersion());
        else secret=cipher.encrypt(request.getPassword());
        DataSourceRecord replacement=new DataSourceRecord(); replacement.setId(id); replacement.setProductId(auth.getProductId());
        apply(replacement,request,secret); replacement.setVersion(request.getVersion());
        replacement.setUpdatedBy(auth.getUserId()); replacement.setUpdatedByName(auth.getDisplayName()); replacement.setUpdatedAt(clock.instant());
        if(mapper.updateCurrent(replacement)!=1) throw conflict(require(auth,id));
        afterCommit(() -> pools.invalidate(id));
        metrics.dataSource("update","success"); return responses.detail(require(auth,id));
    }

    @Transactional
    public void delete(AuthContext auth,String id,long version){
        DataSourceRecord current=require(auth,id); int running=runningTasks.count(id);
        if(running>0){ Map<String,Object> d=new LinkedHashMap<String,Object>(); d.put("runningTaskCount",running);
            throw new ApiException(HttpStatus.CONFLICT,"DATA_SOURCE_IN_USE","数据源正在使用中",d); }
        if(mapper.deleteCurrent(id,auth.getProductId(),version)!=1) throw conflict(current);
        afterCommit(() -> pools.invalidate(id)); metrics.dataSource("delete","success");
    }

    public ConnectionTestResult testUnsaved(ConnectionRequest request){
        ConnectionTestResult result=connectionTester.test(validator.connection(request,request.getPassword(),true));metrics.connectionTest("SUCCESS".equals(result.getStatus())?"success":"failed",result.getFailureCode(),result.getDurationMs());return result;
    }

    @Transactional
    public ConnectionTestResult testSaved(AuthContext auth,String id){
        DataSourceRecord record=require(auth,id); ConnectionTestResult result=connectionTester.test(configuration(record,decrypt(record)));
        mapper.updateLastTest(id,auth.getProductId(),result.getStatus(),clock.instant(),truncate(result.getMessage(),500));
        metrics.connectionTest("SUCCESS".equals(result.getStatus())?"success":"failed",result.getFailureCode(),result.getDurationMs());return result;
    }

    @Transactional(readOnly=true)
    public ConnectionTestResult testEdits(AuthContext auth,String id,ConnectionRequest request){
        DataSourceRecord record=require(auth,id); String password=request.getPassword();
        if(password==null || password.isEmpty()) password=decrypt(record);
        ConnectionTestResult result=connectionTester.test(validator.connection(request,password,false));metrics.connectionTest("SUCCESS".equals(result.getStatus())?"success":"failed",result.getFailureCode(),result.getDurationMs());return result;
    }

    DataSourceRecord require(AuthContext auth,String id){ DataSourceRecord r=mapper.findVisible(id,auth.getProductId()); if(r==null)throw ApiException.notFound(); return r; }
    String decrypt(DataSourceRecord r){ return cipher.decrypt(r.getPasswordCiphertext(),r.getPasswordIv(),r.getKeyVersion()); }
    ConnectionConfiguration configuration(DataSourceRecord r,String password){ return new ConnectionConfiguration(r.getHost(),r.getPort(),r.getUsername(),password,r.getDefaultDatabase(),r.getSslMode(),r.getConnectTimeoutSeconds(),responses.readProperties(r.getPropertiesJson())); }

    private void apply(DataSourceRecord record,ConnectionRequest request,EncryptedCredential secret){
        record.setName(validator.trim(request instanceof CreateDataSourceRequest?((CreateDataSourceRequest)request).getName():((UpdateDataSourceRequest)request).getName()));
        record.setEngine(request instanceof CreateDataSourceRequest?((CreateDataSourceRequest)request).getEngine():((UpdateDataSourceRequest)request).getEngine());
        record.setHost(validator.trim(request.getHost())); record.setPort(request.getPort()); record.setUsername(validator.trim(request.getUsername()));
        record.setPasswordCiphertext(secret.getCiphertext()); record.setPasswordIv(secret.getIv()); record.setKeyVersion(secret.getKeyVersion());
        record.setDefaultDatabase(validator.blankToNull(request.getDefaultDatabase())); record.setSslMode(request.getSslMode());
        record.setConnectTimeoutSeconds(request.getConnectTimeoutSeconds()); record.setPropertiesJson(writeProperties(validator.properties(request)));
        String description=request instanceof CreateDataSourceRequest?((CreateDataSourceRequest)request).getDescription():((UpdateDataSourceRequest)request).getDescription();
        record.setDescription(validator.blankToNull(description));
    }
    private String writeProperties(Map<String,String> properties){ try{return objectMapper.writeValueAsString(properties);}catch(Exception e){throw new IllegalStateException("Cannot serialize JDBC properties");} }
    private String escapeLike(String value){return value.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private ApiException conflict(DataSourceRecord current){Map<String,Object>d=new LinkedHashMap<String,Object>();d.put("currentVersion",current.getVersion());d.put("currentUpdatedAt",current.getUpdatedAt());d.put("currentUpdatedByName",current.getUpdatedByName());return new ApiException(HttpStatus.CONFLICT,"VERSION_CONFLICT","数据源已被其他用户更新",d);}
    private void afterCommit(Runnable action){if(TransactionSynchronizationManager.isSynchronizationActive())TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){action.run();}});else action.run();}
    private String truncate(String v,int max){return v==null?null:(v.length()<=max?v:v.substring(0,max));}
}
