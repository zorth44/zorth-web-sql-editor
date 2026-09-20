package com.bocsoft.sqleditor.metadata.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class TableStatsJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test void serializesNeutralFieldNamesAndNullStats() throws Exception {
        TableDetailResponse empty = new TableDetailResponse("db", "t", java.util.Collections.<ColumnItem>emptyList(),
            null, java.util.Collections.<IndexItem>emptyList(), null, null);
        String json = mapper.writeValueAsString(empty);
        assertThat(json).contains("\"stats\":null").doesNotContain("Rows").doesNotContain("Data_length");

        TableStats stats = new TableStats("MYSQL", 10L, 20L, 30L, 1L, null, null, "c");
        String statsJson = mapper.writeValueAsString(stats);
        assertThat(statsJson).contains("estimatedRows").contains("dataBytes").doesNotContain("Data_length").doesNotContain("\"Rows\"");
    }
}
