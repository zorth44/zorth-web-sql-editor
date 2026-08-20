package com.bocsoft.sqleditor.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.common.SqlEditorMetrics;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.api.ConnectionRequest;
import com.bocsoft.sqleditor.datasource.api.CreateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.connection.CredentialCipher;
import com.bocsoft.sqleditor.datasource.connection.ShortLivedConnectionTester;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceMapper;
import com.bocsoft.sqleditor.datasource.persistence.DataSourceRecord;
import com.bocsoft.sqleditor.engine.EngineId;
import com.bocsoft.sqleditor.engine.EngineRegistry;
import com.bocsoft.sqleditor.engine.EngineSupport;
import com.bocsoft.sqleditor.engine.gbase8a.Gbase8aEngineSupport;
import com.bocsoft.sqleditor.engine.mysql.MysqlEngineSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DataSourceServiceTest {
    private DataSourceMapper mapper; private DataSourceService service; private ObjectMapper json; private AuthContext auth;
    private DataSourceValidator validator;

    @BeforeEach void setUp() {
        mapper = mock(DataSourceMapper.class);
        json = new ObjectMapper().findAndRegisterModules();
        SqlEditorProperties p = new SqlEditorProperties();
        p.getCredentials().setCurrentVersion("v1");
        p.getCredentials().setKeys(Collections.singletonMap("v1", Base64.getEncoder().encodeToString(new byte[32])));
        p.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));
        MysqlEngineSupport mysql = new MysqlEngineSupport();
        EngineRegistry engines = new EngineRegistry(java.util.Arrays.<EngineSupport>asList(
            mysql, new com.bocsoft.sqleditor.engine.postgres.PostgresEngineSupport(), new Gbase8aEngineSupport(mysql)));
        validator = new DataSourceValidator(engines);
        DataSourceResponseMapper responses = new DataSourceResponseMapper(json);
        service = new DataSourceService(mapper, validator, responses, new CredentialCipher(p), new CursorCodec(json, p), json,
            Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC), id -> {}, id -> 0,
            mock(ShortLivedConnectionTester.class), new SqlEditorMetrics(new SimpleMeterRegistry()));
        auth = new AuthContext("u1", "zhangsan", "张三", "p1", "产品", Instant.parse("2026-08-13T01:00:00Z"));
    }

    @Test void createsOwnedEncryptedPasswordFreeResponse() throws Exception {
        when(mapper.insert(any())).thenReturn(1);
        CreateDataSourceRequest request = request();
        String secret = request.getPassword();
        Object response = service.create(auth, request);
        ArgumentCaptor<DataSourceRecord> record = ArgumentCaptor.forClass(DataSourceRecord.class);
        verify(mapper).insert(record.capture());
        assertThat(record.getValue().getProductId()).isEqualTo("p1");
        assertThat(record.getValue().getEngine()).isEqualTo(EngineId.MYSQL);
        assertThat(record.getValue().getPasswordCiphertext()).doesNotContain(secret);
        String serialized = json.writeValueAsString(response);
        assertThat(serialized).doesNotContain(secret).doesNotContain("passwordCiphertext").doesNotContain("productId");
    }

    @Test void rejectsUnregisteredEngineOnCreate() {
        CreateDataSourceRequest request = request();
        request.setEngine("HIVE");
        assertThatThrownBy(() -> service.create(auth, request)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        });
        verifyNoInteractions(mapper);
    }

    @Test void unsavedConnectionTestDefaultsToMysqlEngine() {
        ConnectionRequest request = new ConnectionRequest();
        request.setHost("mysql.internal");
        request.setPort(3306);
        request.setUsername("user");
        request.setPassword("secret");
        request.setSslMode("DISABLED");
        request.setConnectTimeoutSeconds(10);
        request.setProperties(Collections.singletonMap("serverTimezone", "UTC"));
        assertThat(validator.connection(request, "secret", true).getEngine()).isEqualTo(EngineId.MYSQL);
    }

    @Test void postgresqlCreateRequiresDefaultDatabase() {
        CreateDataSourceRequest request = request();
        request.setEngine(EngineId.POSTGRESQL);
        request.setPort(5432);
        request.setDefaultDatabase(null);
        request.setProperties(Collections.singletonMap("ApplicationName", "zorth-sql-editor"));
        assertThatThrownBy(() -> service.create(auth, request)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        });
        verifyNoInteractions(mapper);
    }

    @Test void gbase8aCreateAllowsMissingDefaultDatabase() throws Exception {
        when(mapper.insert(any())).thenReturn(1);
        CreateDataSourceRequest request = request();
        request.setEngine(EngineId.GBASE_8A);
        request.setPort(5258);
        request.setDefaultDatabase(null);
        Object response = service.create(auth, request);
        ArgumentCaptor<DataSourceRecord> record = ArgumentCaptor.forClass(DataSourceRecord.class);
        verify(mapper).insert(record.capture());
        assertThat(record.getValue().getEngine()).isEqualTo(EngineId.GBASE_8A);
        assertThat(json.writeValueAsString(response)).contains("GBASE_8A");
    }

    @Test void unsavedConnectionTestUsesSubmittedEngine() {
        ConnectionRequest request = new ConnectionRequest();
        request.setEngine(EngineId.MYSQL);
        request.setHost("mysql.internal");
        request.setPort(3306);
        request.setUsername("user");
        request.setPassword("secret");
        request.setSslMode("DISABLED");
        request.setConnectTimeoutSeconds(10);
        request.setProperties(Collections.singletonMap("serverTimezone", "UTC"));
        assertThat(validator.connection(request, "secret", true).getEngine()).isEqualTo(EngineId.MYSQL);
    }

    @Test void unsavedConnectionTestRejectsUnregisteredEngine() {
        ConnectionRequest request = new ConnectionRequest();
        request.setEngine("HIVE");
        request.setHost("mysql.internal");
        request.setPort(3306);
        request.setUsername("user");
        request.setPassword("secret");
        request.setSslMode("DISABLED");
        request.setConnectTimeoutSeconds(10);
        request.setProperties(Collections.singletonMap("serverTimezone", "UTC"));
        assertThatThrownBy(() -> validator.connection(request, "secret", true)).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED");
        });
    }

    @Test void scopesListAndInvisibleReadsByProduct() {
        when(mapper.list(eq("p1"), isNull(), isNull(), isNull(), eq(21))).thenReturn(Collections.emptyList());
        service.list(auth, "", 20, null);
        verify(mapper).list(eq("p1"), isNull(), isNull(), isNull(), eq(21));
        when(mapper.findVisible("other", "p1")).thenReturn(null);
        assertThatThrownBy(() -> service.get(auth, "other")).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("DATA_SOURCE_NOT_FOUND"));
    }

    private CreateDataSourceRequest request() {
        CreateDataSourceRequest r = new CreateDataSourceRequest();
        r.setName(" 数据库 ");
        r.setEngine("MYSQL");
        r.setHost("mysql.internal");
        r.setPort(3306);
        r.setUsername(" user ");
        r.setPassword("plain-secret");
        r.setDefaultDatabase("orders");
        r.setSslMode("DISABLED");
        r.setConnectTimeoutSeconds(10);
        r.setProperties(Collections.singletonMap("serverTimezone", "UTC"));
        return r;
    }
}
