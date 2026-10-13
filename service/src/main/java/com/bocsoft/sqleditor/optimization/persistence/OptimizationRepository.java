package com.bocsoft.sqleditor.optimization.persistence;

import com.bocsoft.sqleditor.optimization.api.TaskStatus;
import com.bocsoft.sqleditor.optimization.api.TaskSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit short JDBC transactions; no transaction scope is exposed across Agent HTTP. */
@Repository
public class OptimizationRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    public OptimizationRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
    }
    public <T> T transaction(Supplier<T> action) { return transactions.execute(status -> action.get()); }
    public void lockCreation() { jdbc.queryForObject("SELECT id FROM sql_optimization_quota_lock WHERE id=1 FOR UPDATE", Integer.class); }
    public OptimizationTask byKey(String user, String product, String key) {
        return first(jdbc.query("SELECT * FROM sql_optimization_task WHERE user_id=? AND product_id=? AND idempotency_key=?", TASK, user, product, key));
    }
    public OptimizationTask visible(String id, String user, String product) {
        return first(jdbc.query("SELECT t.* FROM sql_optimization_task t JOIN sql_data_source d ON d.id=t.data_source_id AND d.product_id=t.product_id WHERE t.id=? AND t.user_id=? AND t.product_id=?", TASK, id, user, product));
    }
    public OptimizationTask find(String id) { return first(jdbc.query("SELECT * FROM sql_optimization_task WHERE id=?", TASK, id)); }
    public long unfinished(String user, String product) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_task WHERE status IN ('QUEUED','RUNNING')" + (user == null ? "" : " AND user_id=? AND product_id=?"), Long.class,
            user == null ? new Object[0] : new Object[]{user, product});
    }
    public void insert(OptimizationTask t, OptimizationCredential c) {
        jdbc.update("INSERT INTO sql_optimization_task (id,user_id,product_id,name,data_source_id,data_source_name,engine_id,database_name,statement_text,statement_sha256,source_task_id,idempotency_key,request_fingerprint,status,created_at,expires_at,request_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,'QUEUED',?,?,?)",
            t.id,t.userId,t.productId,t.name,t.datasourceId,t.datasourceName,t.engineId,t.database,t.sql,
            com.bocsoft.sqleditor.optimization.OptimizationDigests.sha256(t.sql),t.sourceTaskId,t.idempotencyKey,t.fingerprint,time(t.createdAt),time(t.expiresAt),t.requestId);
        jdbc.update("INSERT INTO sql_optimization_credential (task_id,ciphertext,key_version,expires_at) VALUES (?,?,?,?)", t.id,c.ciphertext,c.version,time(c.expiresAt));
    }
    public List<OptimizationTask> list(String user, String product, String filter, Instant at, String id, int limit) {
        String query="SELECT t.* FROM sql_optimization_task t JOIN sql_data_source d ON d.id=t.data_source_id AND d.product_id=t.product_id WHERE t.user_id=? AND t.product_id=?";
        java.util.List<Object> args=new java.util.ArrayList<Object>(); args.add(user);args.add(product);
        if(filter!=null){query+=" AND t.status=?";args.add(filter);}
        if(at!=null){query+=" AND (t.created_at<? OR (t.created_at=? AND t.id<?))";args.add(time(at));args.add(time(at));args.add(id);}
        query+=" ORDER BY t.created_at DESC,t.id DESC LIMIT ?";args.add(limit);
        return jdbc.query(query,TASK,args.toArray());
    }
    public OptimizationAttempt claim(Instant now, int budget, int lease, String workerId) {
        return transaction(() -> {
            OptimizationTask t=first(jdbc.query("SELECT * FROM sql_optimization_task WHERE status='QUEUED' ORDER BY created_at,id LIMIT 1",TASK));
            if(t==null)return null;
            OptimizationAttempt a=new OptimizationAttempt();a.id=UUID.randomUUID().toString();a.sessionId=UUID.randomUUID().toString();a.requestId=UUID.randomUUID().toString();a.deadline=now.plusSeconds(budget);
            if(jdbc.update("UPDATE sql_optimization_task SET status='RUNNING',current_attempt_id=?,started_at=?,version=version+1 WHERE id=? AND status='QUEUED' AND version=?",a.id,time(now),t.id,t.version)!=1)return null;
            jdbc.update("INSERT INTO sql_optimization_attempt (id,task_id,attempt_no,session_id,request_id,worker_id,lease_until,hard_deadline,started_at) VALUES (?,?,1,?,?,?,?,?,?)",a.id,t.id,a.sessionId,a.requestId,workerId,time(now.plusSeconds(lease)),time(a.deadline),time(now));
            t.status=TaskStatus.RUNNING;t.attemptId=a.id;t.startedAt=now;t.version++;a.task=t;return a;
        });
    }
    public OptimizationCredential credential(String taskId) {
        List<OptimizationCredential> rows=jdbc.query("SELECT * FROM sql_optimization_credential WHERE task_id=?",(rs,n)->{
            OptimizationCredential c=new OptimizationCredential();c.ciphertext=rs.getString("ciphertext");c.version=rs.getString("key_version");c.expiresAt=instant(rs,"expires_at");return c;
        },taskId);return first(rows);
    }
    public boolean heartbeat(OptimizationAttempt a, Instant now, int lease) {
        Instant until=now.plusSeconds(lease).isBefore(a.deadline)?now.plusSeconds(lease):a.deadline;
        return jdbc.update("UPDATE sql_optimization_attempt a JOIN sql_optimization_task t ON t.current_attempt_id=a.id SET a.lease_until=? WHERE a.id=? AND t.status='RUNNING' AND a.completed_at IS NULL AND a.lease_until>? AND a.hard_deadline>?",time(until),a.id,time(now),time(now))==1;
    }
    public boolean finish(OptimizationAttempt a, Instant now, String report, TaskSummary summary, String error) {
        return transaction(() -> terminal(a.task,now,report,summary,error,a.requestId,true));
    }
    private boolean terminal(OptimizationTask t, Instant now, String report, TaskSummary summary, String error, String requestId, boolean requireLease) {
        String condition="id=? AND status=? AND version=?";
        java.util.List<Object> args=new java.util.ArrayList<Object>();
        args.add(error==null?"SUCCEEDED":"FAILED");args.add(time(now));args.add(report);args.add(report==null?null:1);
        args.add(summary==null?null:summary.getPolicyIssueCount());args.add(summary==null?null:summary.getPerformanceIssueCount());args.add(summary==null?null:summary.getLimitationCount());
        args.add(error);args.add(error==null?null:"扫描未完成，请重新登录后再次扫描");args.add(requestId);args.add(t.id);args.add(t.status.name());args.add(t.version);
        if(t.attemptId!=null){condition+=" AND current_attempt_id=?";args.add(t.attemptId);}
        if(requireLease){condition+=" AND EXISTS (SELECT 1 FROM sql_optimization_attempt a WHERE a.id=current_attempt_id AND a.completed_at IS NULL AND a.lease_until>? AND a.hard_deadline>?)";args.add(time(now));args.add(time(now));}
        if ("WORKER_LOST".equals(error) && !requireLease) {
            condition += " AND EXISTS (SELECT 1 FROM sql_optimization_attempt a WHERE a.id=current_attempt_id AND (a.lease_until<=? OR a.hard_deadline<=?))";
            args.add(time(now)); args.add(time(now));
        }
        int changed=jdbc.update("UPDATE sql_optimization_task SET status=?,completed_at=?,report_json=?,report_schema_version=?,policy_issue_count=?,performance_issue_count=?,limitation_count=?,error_code=?,error_message=?,request_id=?,version=version+1 WHERE "+condition,args.toArray());
        if(changed!=1)return false;
        if(t.attemptId!=null)jdbc.update("UPDATE sql_optimization_attempt SET completed_at=?,outcome=?,error_code=? WHERE id=? AND completed_at IS NULL",time(now),error==null?"SUCCEEDED":"FAILED",error,t.attemptId);
        jdbc.update("DELETE FROM sql_optimization_credential WHERE task_id=?",t.id);return true;
    }
    public void maintain(Instant now, int headroom) {
        transaction(() -> {
            List<OptimizationTask> lost=jdbc.query("SELECT t.* FROM sql_optimization_task t JOIN sql_optimization_attempt a ON a.id=t.current_attempt_id WHERE t.status='RUNNING' AND (a.lease_until<=? OR a.hard_deadline<=?)",TASK,time(now),time(now));
            for(OptimizationTask t:lost)terminal(t,now,null,null,"WORKER_LOST",t.requestId,false);
            List<OptimizationTask> expired=jdbc.query("SELECT t.* FROM sql_optimization_task t LEFT JOIN sql_optimization_credential c ON c.task_id=t.id WHERE t.status='QUEUED' AND (c.task_id IS NULL OR c.expires_at<=?)",TASK,time(now.plusSeconds(headroom)));
            for(OptimizationTask t:expired)terminal(t,now,null,null,"CREDENTIAL_EXPIRED",t.requestId,false);
            jdbc.update("DELETE c FROM sql_optimization_credential c JOIN sql_optimization_task t ON t.id=c.task_id WHERE c.expires_at<=? OR t.status IN ('SUCCEEDED','FAILED')",time(now));
            jdbc.update("DELETE FROM sql_optimization_task WHERE expires_at<=? AND status IN ('SUCCEEDED','FAILED')",time(now));return null;
        });
    }
    public long running() { return jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_task WHERE status='RUNNING'",Long.class); }
    public long queued() { return jdbc.queryForObject("SELECT COUNT(*) FROM sql_optimization_task WHERE status='QUEUED'",Long.class); }
    public double oldestWait(Instant now) {
        Timestamp oldest=jdbc.queryForObject("SELECT MIN(created_at) FROM sql_optimization_task WHERE status='QUEUED'",Timestamp.class);
        return oldest==null?0:Math.max(0,java.time.Duration.between(oldest.toInstant(),now).getSeconds());
    }
    private static <T> T first(List<T> rows) { return rows.isEmpty()?null:rows.get(0); }
    public static Timestamp time(Instant value) { return value==null?null:Timestamp.from(value); }
    private static Instant instant(ResultSet rs,String field)throws SQLException { Timestamp value=rs.getTimestamp(field);return value==null?null:value.toInstant(); }
    private static final RowMapper<OptimizationTask> TASK=(rs,n)->{
        OptimizationTask t=new OptimizationTask();t.id=rs.getString("id");t.userId=rs.getString("user_id");t.productId=rs.getString("product_id");t.name=rs.getString("name");
        t.datasourceId=rs.getString("data_source_id");t.datasourceName=rs.getString("data_source_name");t.engineId=rs.getString("engine_id");t.database=rs.getString("database_name");t.sql=rs.getString("statement_text");
        t.sourceTaskId=rs.getString("source_task_id");t.idempotencyKey=rs.getString("idempotency_key");t.fingerprint=rs.getString("request_fingerprint");t.attemptId=rs.getString("current_attempt_id");t.version=rs.getLong("version");
        t.status=TaskStatus.valueOf(rs.getString("status"));t.reportJson=rs.getString("report_json");t.schemaVersion=(Integer)rs.getObject("report_schema_version");
        t.policyCount=(Integer)rs.getObject("policy_issue_count");t.performanceCount=(Integer)rs.getObject("performance_issue_count");t.limitationCount=(Integer)rs.getObject("limitation_count");
        t.errorCode=rs.getString("error_code");t.errorMessage=rs.getString("error_message");t.requestId=rs.getString("request_id");
        t.createdAt=instant(rs,"created_at");t.startedAt=instant(rs,"started_at");t.completedAt=instant(rs,"completed_at");t.expiresAt=instant(rs,"expires_at");return t;
    };
}
