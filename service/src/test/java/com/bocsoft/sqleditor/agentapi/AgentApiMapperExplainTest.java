package com.bocsoft.sqleditor.agentapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.agentapi.api.AgentSqlExplainResponse;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.PlanFinding;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AgentApiMapperExplainTest {
    private final AgentApiMapper mapper = new AgentApiMapper();

    @Test
    void mapsFullScanEvidenceToHighRisk() {
        PlanEvidence evidence = new PlanEvidence(true, 120_000L, true, Collections.<String>emptyList(),
            Collections.emptyList(),
            Collections.singletonList(new PlanFinding("FULL_SCAN", "scan")), null);
        AgentSqlExplainResponse response = mapper.explain(evidence);
        assertThat(response.isSupported()).isTrue();
        assertThat(response.getRiskLevel()).isEqualTo("HIGH");
        assertThat(response.getFindings()).hasSize(1);
    }

    @Test
    void omitsRiskWhenUnsupported() {
        AgentSqlExplainResponse response = mapper.explain(PlanEvidence.unsupported("EXPLAIN_UNSUPPORTED"));
        assertThat(response.isSupported()).isFalse();
        assertThat(response.getRiskLevel()).isNull();
    }
}
