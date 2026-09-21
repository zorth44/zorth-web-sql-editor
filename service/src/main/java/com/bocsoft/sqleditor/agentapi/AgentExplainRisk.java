package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.PlanFinding;

final class AgentExplainRisk {
    private static final long HIGH_ROWS = 100_000L;
    private static final long MEDIUM_ROWS = 10_000L;

    private AgentExplainRisk() {}

    /**
     * Derives optional riskLevel from plan evidence. Returns null when unsupported or evidence is insufficient.
     */
    static String from(PlanEvidence evidence) {
        if (evidence == null || !evidence.isSupported()) {
            return null;
        }
        boolean hasFullScanFlag = evidence.getFullScan() != null;
        boolean hasRows = evidence.getEstimatedRows() != null;
        boolean hasFindings = evidence.getFindings() != null && !evidence.getFindings().isEmpty();
        if (!hasFullScanFlag && !hasRows && !hasFindings) {
            return null;
        }

        boolean fullScan = Boolean.TRUE.equals(evidence.getFullScan());
        boolean fullScanFinding = false;
        boolean otherFindings = false;
        if (evidence.getFindings() != null) {
            for (PlanFinding finding : evidence.getFindings()) {
                if (finding == null || finding.getCode() == null) {
                    continue;
                }
                if ("FULL_TABLE_SCAN".equals(finding.getCode()) || "FULL_SCAN".equals(finding.getCode())) {
                    fullScanFinding = true;
                } else {
                    otherFindings = true;
                }
            }
        }
        Long rows = evidence.getEstimatedRows();
        if (fullScan || fullScanFinding || (rows != null && rows >= HIGH_ROWS)) {
            return "HIGH";
        }
        if ((rows != null && rows >= MEDIUM_ROWS) || otherFindings) {
            return "MEDIUM";
        }
        if (Boolean.FALSE.equals(evidence.getFullScan())) {
            return "LOW";
        }
        return null;
    }
}
