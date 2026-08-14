package com.bocsoft.sqleditor.export.api;
import javax.validation.constraints.NotBlank;public class SqlExportRequest {@NotBlank private String executionId;private Integer rowLimit;public String getExecutionId(){return executionId;}public void setExecutionId(String v){executionId=v;}public Integer getRowLimit(){return rowLimit;}public void setRowLimit(Integer v){rowLimit=v;}}
