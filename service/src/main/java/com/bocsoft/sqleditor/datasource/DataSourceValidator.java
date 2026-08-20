package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.api.ConnectionRequest;
import com.bocsoft.sqleditor.datasource.api.CreateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.api.UpdateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DataSourceValidator {
    private final EngineRegistry engines;
    public DataSourceValidator(EngineRegistry engines) { this.engines = engines; }

    public void validateCreate(CreateDataSourceRequest request) {
        engines.require(request.getEngine());
        if (!StringUtils.hasLength(request.getPassword())) throw ApiException.validation("password", "REQUIRED", "请输入密码");
        common(request, true);
    }
    public void validateUpdate(UpdateDataSourceRequest request) {
        engines.require(request.getEngine());
        common(request, false);
    }
    public ConnectionConfiguration connection(ConnectionRequest request, String password, boolean requirePassword) {
        common(request, requirePassword);
        if (requirePassword && !StringUtils.hasLength(password)) throw ApiException.validation("password", "REQUIRED", "请输入密码");
        return new ConnectionConfiguration(engineOf(request), trim(request.getHost()), request.getPort(), trim(request.getUsername()),
            password, blankToNull(request.getDefaultDatabase()), request.getSslMode(),
            request.getConnectTimeoutSeconds(), properties(request));
    }
    public String trim(String value) { return value == null ? null : value.trim(); }
    public String blankToNull(String value) {
        String trimmed=trim(value); return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
    public Map<String,String> properties(ConnectionRequest request) {
        return engine(request).validateProperties(request.getProperties());
    }

    private void common(ConnectionRequest request, boolean requirePassword) {
        NetworkPolicy.validateHost(request.getHost());
        if (!StringUtils.hasText(request.getUsername())) throw ApiException.validation("username", "REQUIRED", "请输入用户名");
        if (!isSslMode(request.getSslMode())) throw ApiException.validation("sslMode", "INVALID", "SSL 模式不合法");
        if (requirePassword && !StringUtils.hasLength(request.getPassword())) throw ApiException.validation("password", "REQUIRED", "请输入密码");
        engine(request).validateProperties(request.getProperties());
        EngineSupport selected = engine(request);
        if (selected.defaultNamespaceRequired() && !StringUtils.hasText(request.getDefaultDatabase())) {
            throw ApiException.validation("defaultDatabase", "REQUIRED", "请输入数据库名");
        }
        if (StringUtils.hasText(request.getDefaultDatabase())) {
            selected.validateIdentifier("defaultDatabase", request.getDefaultDatabase());
        }
    }
    private EngineSupport engine(ConnectionRequest request) {
        return engines.require(engineOf(request));
    }
    private String engineOf(ConnectionRequest request) {
        if (StringUtils.hasText(request.getEngine())) return request.getEngine();
        if (request instanceof CreateDataSourceRequest || request instanceof UpdateDataSourceRequest) return request.getEngine();
        return EngineId.MYSQL;
    }
    private boolean isSslMode(String mode) { return "DISABLED".equals(mode) || "PREFERRED".equals(mode) || "REQUIRED".equals(mode); }
}
