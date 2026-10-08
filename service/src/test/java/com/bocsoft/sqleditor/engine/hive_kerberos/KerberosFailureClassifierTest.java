package com.bocsoft.sqleditor.engine.hive_kerberos;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.ConnectionFailure;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class KerberosFailureClassifierTest {
    private final KerberosFailureClassifier classifier = new KerberosFailureClassifier();

    @Test void mapsKerberosAndGssFailuresToAuthenticationFailed() {
        assertThat(classifier.classify(new SQLException("GSS initiate failed for principal hive@REALM")).getCode())
            .isEqualTo("AUTHENTICATION_FAILED");
        assertThat(classifier.classify(new IllegalStateException("krb5 error")).getCode())
            .isEqualTo("AUTHENTICATION_FAILED");
        assertThat(classifier.classify(new SQLException("SASL authentication failed")).getCode())
            .isEqualTo("AUTHENTICATION_FAILED");
    }

    @Test void mapsOtherFailuresToConnectionFailed() {
        assertThat(classifier.classify(new SQLException("connection refused")).getCode())
            .isEqualTo("CONNECTION_FAILED");
        assertThat(classifier.classify(new RuntimeException((String) null)).getCode())
            .isEqualTo("CONNECTION_FAILED");
    }

    @Test void classifiesTheWrappedCauseNotTheNeutralWrapperMessage() {
        ConnectionFailure failure = classifier.classify(
            new SQLException("Service-discovery connection failed", new java.net.ConnectException("refused")));
        assertThat(failure.getCode()).isEqualTo("CONNECTION_FAILED");
    }

    @Test void neverLeaksOriginalMessage() {
        ConnectionFailure failure = classifier.classify(
            new SQLException("keytab /opt/keytab/hive531/hive.keytab realm SECRET-REALM"));
        assertThat(failure.getMessage()).doesNotContain("/opt/keytab").doesNotContain("SECRET-REALM");
    }
}
