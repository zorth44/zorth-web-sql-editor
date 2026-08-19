package com.bocsoft.sqleditor.datasource.connection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.bocsoft.sqleditor.engine.postgres.PostgresEngineSupport;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class NetworkAndJdbcSecurityTest {
    private final MysqlEngineSupport mysql = new MysqlEngineSupport();
    private final PostgresEngineSupport postgres = new PostgresEngineSupport();
    private final EngineRegistry engines = new EngineRegistry(
        java.util.Arrays.<EngineSupport>asList(mysql, postgres));

    @Test void handlesIpv4AndIpv6CidrBoundaries()throws Exception{
        CidrBlock v4=CidrBlock.parse("10.0.0.0/8");
        assertThat(v4.contains(InetAddress.getByName("10.255.1.2"))).isTrue();
        assertThat(v4.contains(InetAddress.getByName("11.0.0.1"))).isFalse();
        CidrBlock v6=CidrBlock.parse("2001:db8::/32");
        assertThat(v6.contains(InetAddress.getByName("2001:db8::1"))).isTrue();
        assertThat(v6.contains(InetAddress.getByName("2001:db9::1"))).isFalse();
        assertThatThrownBy(()->CidrBlock.parse("10.0.0.0/99")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new NetworkPolicy(Collections.singletonList("invalid-cidr"),Collections.<String>emptyList(),host->new InetAddress[0])).isInstanceOf(IllegalStateException.class);
    }

    @Test void rejectsMixedDnsAnswersAndUnsafeHosts()throws Exception{
        NetworkPolicy mixed=new NetworkPolicy(Collections.singletonList("10.0.0.0/8"),Collections.<String>emptyList(),host->new InetAddress[]{InetAddress.getByName("10.0.0.1"),InetAddress.getByName("127.0.0.1")});
        assertThatThrownBy(()->mixed.resolve("mysql.internal")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->NetworkPolicy.validateHost("https://mysql.internal/path")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->NetworkPolicy.validateHost("fe80::1%lo0")).isInstanceOf(ApiException.class);
    }

    @Test void buildsPinnedIpv6AndFixedProperties()throws Exception{
        NetworkPolicy network=new NetworkPolicy(Collections.singletonList("2001:db8::/32"),Collections.<String>emptyList(),host->new InetAddress[]{InetAddress.getByName("2001:db8::5")});
        JdbcConfigurationBuilder builder=new JdbcConfigurationBuilder(network,engines);
        Map<String,String> user=new LinkedHashMap<String,String>();
        user.put("serverTimezone","Asia/Shanghai");
        user.put("tinyInt1isBit","false");
        JdbcTarget target=builder.build(new ConnectionConfiguration("db",3306,"user","secret","orders","REQUIRED",10,user));
        assertThat(target.getUrls().get(0)).contains("jdbc:mysql://[2001:db8:").endsWith("/orders");
        Properties properties=target.copyProperties();
        assertThat(properties.getProperty("allowMultiQueries")).isEqualTo("false");
        assertThat(properties.getProperty("useUnicode")).isEqualTo("true");
        assertThat(properties.getProperty("requireSSL")).isEqualTo("true");
        assertThat(properties.getProperty("password")).isEqualTo("secret");
    }

    @Test void rejectsPropertyInjectionAndSanitizesFailures(){
        assertThatThrownBy(()->mysql.validateProperties(Collections.singletonMap("allowMultiQueries","true"))).isInstanceOf(ApiException.class);
        assertThat(mysql.classifyConnectionFailure(new SQLException("sensitive jdbc:mysql://host", "28000",1045)).getCode()).isEqualTo("AUTHENTICATION_FAILED");
        assertThat(mysql.classifyConnectionFailure(new java.net.ConnectException("secret target")).getCode()).isEqualTo("CONNECTION_REFUSED");
        assertThat(mysql.classifyConnectionFailure(new java.net.SocketTimeoutException("secret target")).getCode()).isEqualTo("CONNECTION_TIMEOUT");
        assertThat(mysql.classifyConnectionFailure(new javax.net.ssl.SSLException("certificate secret")).getCode()).isEqualTo("TLS_FAILED");
        assertThat(mysql.classifyConnectionFailure(new SQLException("anything")).getMessage()).doesNotContain("anything").doesNotContain("jdbc:mysql");
    }

    @Test void buildsPinnedPostgresqlIpv6UrlAndKeepsDatabase() throws Exception {
        NetworkPolicy network = new NetworkPolicy(Collections.singletonList("2001:db8::/32"), Collections.<String>emptyList(), host -> new InetAddress[]{InetAddress.getByName("2001:db8::5")});
        JdbcConfigurationBuilder builder = new JdbcConfigurationBuilder(network, engines);
        JdbcTarget target = builder.build(new ConnectionConfiguration(EngineId.POSTGRESQL, "db", 5432, "user", "secret", "orders", "REQUIRED", 10, Collections.singletonMap("ApplicationName", "zorth-sql-editor")));
        assertThat(target.getUrls().get(0)).contains("jdbc:postgresql://[2001:db8:").endsWith("/orders");
        assertThat(postgres.jdbcUrlWithoutNamespace(target.getUrls().get(0))).endsWith("/orders");
        Properties properties = target.copyProperties();
        assertThat(properties.getProperty("sslmode")).isEqualTo("require");
        assertThat(properties.getProperty("allowMultiQueries")).isNull();
        assertThat(properties.getProperty("password")).isEqualTo("secret");
    }
}
