package com.bocsoft.sqleditor.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.Collections;
import javax.validation.Validation;
import javax.validation.Validator;
import org.junit.jupiter.api.Test;

class SqlEditorPropertiesTest {
    @Test void rejectsMissingSecretsAndUnsafeBounds(){SqlEditorProperties empty=new SqlEditorProperties();Validator validator=Validation.buildDefaultValidatorFactory().getValidator();assertThat(validator.validate(empty)).extracting(value->value.getPropertyPath().toString()).contains("auth.contextUrl","auth.internalServiceKey","cursor.signingKey");assertThatThrownBy(empty::validateConfiguration).isInstanceOf(IllegalStateException.class);SqlEditorProperties valid=valid();valid.getAuth().setCacheTtlSeconds(61);assertThatThrownBy(valid::validateConfiguration).isInstanceOf(IllegalStateException.class).hasMessageContaining("TTL");valid=valid();valid.getPools().setMaxPools(11);assertThatThrownBy(valid::validateConfiguration).isInstanceOf(IllegalStateException.class).hasMessageContaining("Pool limits");valid=valid();valid.getNetwork().setAllowedCidrs(Collections.singletonList("invalid-cidr"));assertThatThrownBy(valid::validateConfiguration).isInstanceOf(IllegalStateException.class).hasMessageContaining("CIDR");valid=valid();valid.getHttp().setTrustedProxyCidrs(Collections.singletonList("not-a-cidr"));assertThatThrownBy(valid::validateConfiguration).isInstanceOf(IllegalStateException.class).hasMessageContaining("CIDR");valid=valid();valid.getHttp().setTrustedProxyCidrs(Collections.singletonList(""));valid.validateConfiguration();assertThat(valid.getExplainAnalyze().isEnabled()).isFalse();assertThat(valid.getExplainAnalyze().getTimeoutSeconds()).isEqualTo(15);assertThat(valid.getAgentApi().getMaxRowLimit()).isEqualTo(1000);assertThat(valid.getAgentApi().getMaxUniqueKeys()).isEqualTo(32);assertThat(valid.getAgentApi().getMaxRelationshipsPerPage()).isEqualTo(50);}
    @Test void absentKerberosConfigurationLeavesTheFeatureDisabled() {
        SqlEditorProperties properties = valid();
        properties.validateConfiguration();
    }

    @Test void presentButIncompleteKerberosConfigurationFailsStartup() {
        SqlEditorProperties properties = valid();
        properties.getKerberos().setKeytabBasePath("/opt/keytab");
        properties.getKerberos().getEnvironments().put("prod", new SqlEditorProperties.Kerberos.Environment());
        assertThatThrownBy(properties::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Kerberos environment prod");
    }

    @Test void blankKerberosKeytabBasePathFailsStartup() {
        SqlEditorProperties properties = valid();
        SqlEditorProperties.Kerberos.Environment env = new SqlEditorProperties.Kerberos.Environment();
        env.setZookeeperQuorum("zk.internal:2181");
        env.setKrb5Conf("/etc/krb5.conf");
        env.setRealm("REALM");
        properties.getKerberos().getEnvironments().put("prod", env);
        assertThatThrownBy(properties::validateConfiguration)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("keytab base path");
    }

    @Test void completeKerberosConfigurationPassesValidation() {
        SqlEditorProperties properties = valid();
        properties.getKerberos().setKeytabBasePath("/opt/keytab");
        SqlEditorProperties.Kerberos.Environment env = new SqlEditorProperties.Kerberos.Environment();
        env.setZookeeperQuorum("zk.internal:2181");
        env.setKrb5Conf("/etc/krb5.conf");
        env.setRealm("REALM");
        properties.getKerberos().getEnvironments().put("prod", env);
        properties.validateConfiguration();
    }

    private SqlEditorProperties valid(){SqlEditorProperties properties=new SqlEditorProperties();properties.getAuth().setContextUrl("http://auth.invalid/context");properties.getAuth().setInternalServiceKey("key");properties.getCursor().setSigningKey(Base64.getEncoder().encodeToString(new byte[32]));properties.getCredentials().setCurrentVersion("v1");properties.getCredentials().setKeys(Collections.singletonMap("v1",Base64.getEncoder().encodeToString(new byte[32])));properties.getNetwork().setAllowedCidrs(Collections.singletonList("10.0.0.0/8"));return properties;}
}
