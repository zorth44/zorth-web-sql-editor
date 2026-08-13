package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.api.ConnectionRequest;
import com.bocsoft.sqleditor.datasource.api.CreateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.api.UpdateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.JdbcPropertyValidator;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DataSourceValidator {
    private final JdbcPropertyValidator jdbcProperties;
    public DataSourceValidator(JdbcPropertyValidator jdbcProperties) { this.jdbcProperties=jdbcProperties; }

    public void validateCreate(CreateDataSourceRequest request) {
        if (!"MYSQL".equals(request.getEngine())) throw ApiException.validation("engine", "INVALID", "数据库类型仅支持 MYSQL");
        if (!StringUtils.hasLength(request.getPassword())) throw ApiException.validation("password", "REQUIRED", "请输入密码");
        common(request, true);
    }
    public void validateUpdate(UpdateDataSourceRequest request) {
        if (!"MYSQL".equals(request.getEngine())) throw ApiException.validation("engine", "INVALID", "数据库类型仅支持 MYSQL");
        common(request, false);
    }
    public ConnectionConfiguration connection(ConnectionRequest request, String password, boolean requirePassword) {
        common(request, requirePassword);
        if (requirePassword && !StringUtils.hasLength(password)) throw ApiException.validation("password", "REQUIRED", "请输入密码");
        return new ConnectionConfiguration(trim(request.getHost()), request.getPort(), trim(request.getUsername()),
            password, blankToNull(request.getDefaultDatabase()), request.getSslMode(),
            request.getConnectTimeoutSeconds(), jdbcProperties.validate(request.getProperties()));
    }
    public String trim(String value) { return value == null ? null : value.trim(); }
    public String blankToNull(String value) {
        String trimmed=trim(value); return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
    public Map<String,String> properties(ConnectionRequest request) { return jdbcProperties.validate(request.getProperties()); }

    private void common(ConnectionRequest request, boolean requirePassword) {
        NetworkPolicy.validateHost(request.getHost());
        if (!StringUtils.hasText(request.getUsername())) throw ApiException.validation("username", "REQUIRED", "请输入用户名");
        if (!isSslMode(request.getSslMode())) throw ApiException.validation("sslMode", "INVALID", "SSL 模式不合法");
        if (requirePassword && !StringUtils.hasLength(request.getPassword())) throw ApiException.validation("password", "REQUIRED", "请输入密码");
        jdbcProperties.validate(request.getProperties());
    }
    private boolean isSslMode(String mode) { return "DISABLED".equals(mode) || "PREFERRED".equals(mode) || "REQUIRED".equals(mode); }
}
