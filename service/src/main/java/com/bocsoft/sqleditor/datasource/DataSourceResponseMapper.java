package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.datasource.api.DataSourceDetailResponse;
import com.bocsoft.sqleditor.datasource.api.DataSourceListItemResponse;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DataSourceResponseMapper {
    private final ObjectMapper objectMapper;
    public DataSourceResponseMapper(ObjectMapper objectMapper) { this.objectMapper=objectMapper; }
    public DataSourceListItemResponse listItem(DataSourceRecord record) {
        DataSourceListItemResponse result=new DataSourceListItemResponse();
        common(record,result);
        return result;
    }
    public DataSourceDetailResponse detail(DataSourceRecord record) {
        DataSourceDetailResponse result=new DataSourceDetailResponse();
        common(record,result);
        result.setConnectTimeoutSeconds(record.getConnectTimeoutSeconds());
        result.setProperties(readProperties(record.getPropertiesJson()));
        result.setDescription(record.getDescription());
        result.setLastTestMessage(record.getLastTestMessage());
        result.setCreatedBy(record.getCreatedBy());
        result.setCreatedByName(record.getCreatedByName());
        result.setCreatedAt(record.getCreatedAt());
        return result;
    }
    public Map<String,String> readProperties(String json) {
        try {
            if (json == null || json.trim().isEmpty()) return Collections.emptyMap();
            return objectMapper.readValue(json, new TypeReference<Map<String,String>>() { });
        } catch (Exception exception) { throw new IllegalStateException("Stored JDBC properties are invalid"); }
    }
    private void common(DataSourceRecord record, DataSourceListItemResponse result) {
        result.setId(record.getId()); result.setName(record.getName()); result.setEngine(record.getEngine());
        result.setHost(record.getHost()); result.setPort(record.getPort()); result.setUsername(record.getUsername());
        result.setPasswordConfigured(record.getPasswordCiphertext()!=null && !record.getPasswordCiphertext().isEmpty());
        result.setDefaultDatabase(record.getDefaultDatabase()); result.setSslMode(record.getSslMode());
        result.setLastTestStatus(record.getLastTestStatus()); result.setLastTestAt(record.getLastTestAt());
        result.setVersion(record.getVersion()); result.setUpdatedBy(record.getUpdatedBy());
        result.setUpdatedByName(record.getUpdatedByName()); result.setUpdatedAt(record.getUpdatedAt());
    }
}
