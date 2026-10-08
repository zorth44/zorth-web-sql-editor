package com.bocsoft.sqleditor.engine.hive_kerberos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import com.bocsoft.sqleditor.config.SqlEditorProperties;
import com.bocsoft.sqleditor.datasource.connection.NetworkPolicy;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class KerberosEndpointsTest {
    private static NetworkPolicy policy() {
        SqlEditorProperties properties = new SqlEditorProperties();
        properties.getNetwork().setAllowedCidrs(Collections.singletonList("10.0.0.0/8"));
        return new NetworkPolicy(properties);
    }

    @Test void acceptsAllowedQuorumEndpoints() {
        KerberosEndpoints.assertAllowed(policy(), "10.1.2.3:2181,10.4.5.6:2181");
    }

    @Test void rejectsOutOfRangeQuorumEndpoint() {
        assertThatThrownBy(() -> KerberosEndpoints.assertAllowed(policy(), "10.1.2.3:2181,192.168.1.1:2181"))
            .isInstanceOfSatisfying(ApiException.class, e ->
                assertThat(e.getCode()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test void rejectsBlankQuorum() {
        assertThatThrownBy(() -> KerberosEndpoints.assertAllowed(policy(), "  "))
            .isInstanceOf(ApiException.class);
    }

    @Test void parsesHostFromEndpoint() {
        assertThat(KerberosEndpoints.hostOf("zk.internal:2181")).isEqualTo("zk.internal");
        assertThat(KerberosEndpoints.hostOf("[2001:db8::1]:2181")).isEqualTo("2001:db8::1");
        assertThat(KerberosEndpoints.hostOf("zk.internal")).isEqualTo("zk.internal");
    }
}
