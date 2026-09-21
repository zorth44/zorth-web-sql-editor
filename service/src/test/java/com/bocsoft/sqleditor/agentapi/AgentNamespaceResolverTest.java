package com.bocsoft.sqleditor.agentapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.common.ApiException;
import org.junit.jupiter.api.Test;

class AgentNamespaceResolverTest {

    @Test
    void prefersSinglePresentField() {
        assertThat(AgentNamespaceResolver.resolve("business", null)).isEqualTo("business");
        assertThat(AgentNamespaceResolver.resolve(null, "public")).isEqualTo("public");
        assertThat(AgentNamespaceResolver.resolve("  business  ", "  ")).isEqualTo("business");
        assertThat(AgentNamespaceResolver.resolve("", "public")).isEqualTo("public");
    }

    @Test
    void acceptsEqualPair() {
        assertThat(AgentNamespaceResolver.resolve("public", "public")).isEqualTo("public");
        assertThat(AgentNamespaceResolver.resolve("  public ", "public")).isEqualTo("public");
    }

    @Test
    void rejectsUnequalPair() {
        assertThatThrownBy(() -> AgentNamespaceResolver.resolve("business", "public"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void bothAbsentYieldsNull() {
        assertThat(AgentNamespaceResolver.resolve(null, null)).isNull();
        assertThat(AgentNamespaceResolver.resolve("  ", "")).isNull();
    }
}
