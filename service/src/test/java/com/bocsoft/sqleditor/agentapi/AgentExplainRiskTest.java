package com.bocsoft.sqleditor.agentapi;

import static org.assertj.core.api.Assertions.assertThat;

import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.PlanFinding;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class AgentExplainRiskTest {

    @Test
    void omitsWhenUnsupportedOrThinEvidence() {
        assertThat(AgentExplainRisk.from(PlanEvidence.unsupported("EXPLAIN_UNSUPPORTED"))).isNull();
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, null, null, Collections.<String>emptyList(),
            Collections.emptyList(), Collections.<PlanFinding>emptyList(), null))).isNull();
    }

    @Test
    void classifiesHighForFullScanAndLargeEstimates() {
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, 10L, true, Collections.<String>emptyList(),
            Collections.emptyList(), Collections.<PlanFinding>emptyList(), null))).isEqualTo("HIGH");
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, null, false, Collections.<String>emptyList(),
            Collections.emptyList(),
            Collections.singletonList(new PlanFinding("FULL_TABLE_SCAN", "scan")), null))).isEqualTo("HIGH");
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, null, false, Collections.<String>emptyList(),
            Collections.emptyList(),
            Collections.singletonList(new PlanFinding("FULL_SCAN", "scan")), null))).isEqualTo("HIGH");
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, 100_000L, false, Collections.<String>emptyList(),
            Collections.emptyList(), Collections.<PlanFinding>emptyList(), null))).isEqualTo("HIGH");
    }

    @Test
    void classifiesMediumForMidEstimatesOrOtherFindings() {
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, 10_000L, false, Collections.<String>emptyList(),
            Collections.emptyList(), Collections.<PlanFinding>emptyList(), null))).isEqualTo("MEDIUM");
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, 100L, false, Collections.<String>emptyList(),
            Collections.emptyList(),
            Collections.singletonList(new PlanFinding("INDEX_MISMATCH", "idx")), null))).isEqualTo("MEDIUM");
    }

    @Test
    void classifiesLowForExplicitNonFullScan() {
        assertThat(AgentExplainRisk.from(new PlanEvidence(true, 100L, false, Collections.<String>emptyList(),
            Collections.emptyList(), Collections.<PlanFinding>emptyList(), null))).isEqualTo("LOW");
    }
}
