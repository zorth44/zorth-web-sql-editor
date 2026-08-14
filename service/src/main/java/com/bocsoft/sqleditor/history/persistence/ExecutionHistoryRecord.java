package com.bocsoft.sqleditor.history.persistence;

import java.time.Instant;

public class ExecutionHistoryRecord {
    private String id; private String userId; private String username; private String productId;
    private String dataSourceId; private String dataSourceName; private String databaseName;
    private String operation; private String statementText; private String statementHash;
    private String statementType; private String status; private String resultKind;
    private Long returnedRows; private Long affectedRows; private boolean truncated; private Long durationMs;
    private Integer mysqlErrorCode; private String sqlState; private String errorMessage;
    private String requestId; private String clientIp; private Instant startedAt; private Instant finishedAt;
    public String getId(){return id;} public void setId(String v){id=v;}
    public String getUserId(){return userId;} public void setUserId(String v){userId=v;}
    public String getUsername(){return username;} public void setUsername(String v){username=v;}
    public String getProductId(){return productId;} public void setProductId(String v){productId=v;}
    public String getDataSourceId(){return dataSourceId;} public void setDataSourceId(String v){dataSourceId=v;}
    public String getDataSourceName(){return dataSourceName;} public void setDataSourceName(String v){dataSourceName=v;}
    public String getDatabaseName(){return databaseName;} public void setDatabaseName(String v){databaseName=v;}
    public String getOperation(){return operation;} public void setOperation(String v){operation=v;}
    public String getStatementText(){return statementText;} public void setStatementText(String v){statementText=v;}
    public String getStatementHash(){return statementHash;} public void setStatementHash(String v){statementHash=v;}
    public String getStatementType(){return statementType;} public void setStatementType(String v){statementType=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public String getResultKind(){return resultKind;} public void setResultKind(String v){resultKind=v;}
    public Long getReturnedRows(){return returnedRows;} public void setReturnedRows(Long v){returnedRows=v;}
    public Long getAffectedRows(){return affectedRows;} public void setAffectedRows(Long v){affectedRows=v;}
    public boolean isTruncated(){return truncated;} public void setTruncated(boolean v){truncated=v;}
    public Long getDurationMs(){return durationMs;} public void setDurationMs(Long v){durationMs=v;}
    public Integer getMysqlErrorCode(){return mysqlErrorCode;} public void setMysqlErrorCode(Integer v){mysqlErrorCode=v;}
    public String getSqlState(){return sqlState;} public void setSqlState(String v){sqlState=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public String getRequestId(){return requestId;} public void setRequestId(String v){requestId=v;}
    public String getClientIp(){return clientIp;} public void setClientIp(String v){clientIp=v;}
    public Instant getStartedAt(){return startedAt;} public void setStartedAt(Instant v){startedAt=v;}
    public Instant getFinishedAt(){return finishedAt;} public void setFinishedAt(Instant v){finishedAt=v;}
}
