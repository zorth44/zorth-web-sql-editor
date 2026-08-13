package com.bocsoft.sqleditor.datasource.connection;

import java.io.UnsupportedEncodingException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.stereotype.Component;

@Component
public class JdbcConfigurationBuilder {
    private final NetworkPolicy networkPolicy;
    private final JdbcPropertyValidator propertyValidator;
    public JdbcConfigurationBuilder(NetworkPolicy networkPolicy, JdbcPropertyValidator propertyValidator) {
        this.networkPolicy=networkPolicy; this.propertyValidator=propertyValidator;
    }

    public JdbcTarget build(ConnectionConfiguration configuration) {
        ResolvedTarget target = networkPolicy.resolve(configuration.getHost());
        Map<String,String> user = propertyValidator.validate(configuration.getProperties());
        Properties properties = new Properties();
        for (Map.Entry<String,String> entry : user.entrySet()) properties.setProperty(entry.getKey(), entry.getValue());
        applySsl(properties, configuration.getSslMode());
        properties.setProperty("allowMultiQueries", "false");
        properties.setProperty("allowLoadLocalInfile", "false");
        properties.setProperty("allowUrlInLocalInfile", "false");
        properties.setProperty("autoDeserialize", "false");
        properties.setProperty("useUnicode", "true");
        properties.setProperty("characterEncoding", "UTF-8");
        properties.setProperty("user", configuration.getUsername());
        properties.setProperty("password", configuration.getPassword());
        List<String> urls = new ArrayList<String>();
        for (InetAddress address : target.getAddresses()) {
            String host = address instanceof Inet6Address ? "[" + address.getHostAddress() + "]" : address.getHostAddress();
            urls.add("jdbc:mysql://" + host + ":" + configuration.getPort() + "/" + encode(configuration.getDefaultDatabase()));
        }
        return new JdbcTarget(urls, properties);
    }

    private void applySsl(Properties properties, String mode) {
        if ("DISABLED".equals(mode)) {
            properties.setProperty("useSSL", "false");
        } else if ("PREFERRED".equals(mode)) {
            properties.setProperty("useSSL", "true"); properties.setProperty("requireSSL", "false");
            properties.setProperty("verifyServerCertificate", "false");
        } else if ("REQUIRED".equals(mode)) {
            properties.setProperty("useSSL", "true"); properties.setProperty("requireSSL", "true");
            properties.setProperty("verifyServerCertificate", "false");
        } else throw com.bocsoft.sqleditor.common.ApiException.validation("sslMode", "INVALID", "SSL 模式不合法");
    }

    private String encode(String value) {
        if (value == null || value.isEmpty()) return "";
        try { return URLEncoder.encode(value, "UTF-8").replace("+", "%20"); }
        catch (UnsupportedEncodingException exception) { throw new IllegalStateException("UTF-8 unavailable", exception); }
    }
}
