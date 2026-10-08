package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.datasource.api.ConnectionRequest;
import com.bocsoft.sqleditor.datasource.api.CreateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.api.UpdateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.connection.ConnectionConfiguration;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import com.bocsoft.sqleditor.engine.EngineDescriptor;
import com.bocsoft.sqleditor.engine.EngineField;
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
        validate(engines.require(engineOf(request)), request, true);
    }
    public void validateUpdate(UpdateDataSourceRequest request) {
        validate(engines.require(engineOf(request)), request, false);
    }
    public ConnectionConfiguration connection(ConnectionRequest request, String password, boolean requirePassword) {
        EngineSupport engine = engines.require(engineOf(request));
        validate(engine, request, requirePassword);
        int timeout = request.getConnectTimeoutSeconds() == null ? 10 : request.getConnectTimeoutSeconds().intValue();
        int port = request.getPort() == null ? 0 : request.getPort().intValue();
        return new ConnectionConfiguration(engine.id(), trim(request.getHost()), port, trim(request.getUsername()),
            password, blankToNull(request.getDefaultDatabase()), request.getSslMode(), timeout,
            properties(request), blankToNull(request.getEnvironment()), blankToNull(request.getKeytabFile()),
            blankToNull(request.getQueueName()));
    }
    public String trim(String value) { return value == null ? null : value.trim(); }
    public String blankToNull(String value) {
        String trimmed=trim(value); return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
    public Map<String,String> properties(ConnectionRequest request) {
        return engine(request).validateProperties(request.getProperties());
    }

    private void validate(EngineSupport engine, ConnectionRequest request, boolean requirePassword) {
        EngineDescriptor descriptor = engine.descriptor();
        if (engine.requiresHostResolution()) NetworkPolicy.validateHost(request.getHost());
        for (EngineField field : descriptor.getConnectionFields()) {
            String name = field.getName();
            String value = fieldValue(request, name);
            boolean required = field.isRequired()
                || (Boolean.TRUE.equals(field.getRequiredOnCreate()) && requirePassword);
            if (required && !StringUtils.hasText(value)) {
                throw ApiException.validation(name, "REQUIRED", "请输入" + label(field, name));
            }
            if (value != null && field.getMaxLength() != null && value.length() > field.getMaxLength().intValue()) {
                throw ApiException.validation(name, "OUT_OF_RANGE", label(field, name) + "长度超出限制");
            }
            if (value != null && (field.getMin() != null || field.getMax() != null)) {
                int parsed = parseNumber(name, value);
                if (field.getMin() != null && parsed < field.getMin().intValue()) {
                    throw ApiException.validation(name, "OUT_OF_RANGE", label(field, name) + "超出允许范围");
                }
                if (field.getMax() != null && parsed > field.getMax().intValue()) {
                    throw ApiException.validation(name, "OUT_OF_RANGE", label(field, name) + "超出允许范围");
                }
            }
        }
        if (declaresField(descriptor, "sslMode") && StringUtils.hasText(request.getSslMode())
            && !isSslMode(request.getSslMode())) {
            throw ApiException.validation("sslMode", "INVALID", "SSL 模式不合法");
        }
        if (engine.defaultNamespaceRequired() && !StringUtils.hasText(request.getDefaultDatabase())) {
            throw ApiException.validation("defaultDatabase", "REQUIRED", "请输入数据库名");
        }
        if (StringUtils.hasText(request.getDefaultDatabase())) {
            engine.validateIdentifier("defaultDatabase", request.getDefaultDatabase());
        }
        engine.validateProperties(request.getProperties());
    }

    private boolean declaresField(EngineDescriptor descriptor, String name) {
        for (EngineField field : descriptor.getConnectionFields()) if (name.equals(field.getName())) return true;
        return false;
    }

    private String fieldValue(ConnectionRequest request, String name) {
        if ("host".equals(name)) return trim(request.getHost());
        if ("port".equals(name)) return request.getPort() == null ? null : String.valueOf(request.getPort());
        if ("username".equals(name)) return trim(request.getUsername());
        if ("password".equals(name)) return request.getPassword();
        if ("defaultDatabase".equals(name)) return trim(request.getDefaultDatabase());
        if ("sslMode".equals(name)) return request.getSslMode();
        if ("connectTimeoutSeconds".equals(name)) {
            return request.getConnectTimeoutSeconds() == null ? null : String.valueOf(request.getConnectTimeoutSeconds());
        }
        if ("environment".equals(name)) return trim(request.getEnvironment());
        if ("keytabFile".equals(name)) return trim(request.getKeytabFile());
        if ("queueName".equals(name)) return trim(request.getQueueName());
        return null;
    }

    private int parseNumber(String name, String value) {
        try { return Integer.parseInt(value.trim()); }
        catch (NumberFormatException exception) { throw ApiException.validation(name, "INVALID", "请输入合法数字"); }
    }

    private String label(EngineField field, String name) {
        return StringUtils.hasText(field.getLabel()) ? field.getLabel() : name;
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
