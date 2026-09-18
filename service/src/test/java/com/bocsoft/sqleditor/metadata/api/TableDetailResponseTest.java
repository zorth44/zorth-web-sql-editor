package com.bocsoft.sqleditor.metadata.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TableDetailResponseTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesNeutralStatsAndOmitsMysqlStatusColumnNames() throws Exception {
        TableDetailResponse missing = new TableDetailResponse(
            "orders", "t", Collections.<ColumnItem>emptyList(), null, Collections.<IndexItem>emptyList(), "CREATE TABLE t", null);
        JsonNode missingJson = mapper.readTree(mapper.writeValueAsString(missing));
        assertThat(missingJson.path("stats").isNull()).isTrue();

        TableStats stats = new TableStats("InnoDB", 12L, 100L, 20L, 13L, "2020-01-01T00:00:00Z", null, "订单");
        TableDetailResponse present = new TableDetailResponse(
            "orders", "t", Collections.<ColumnItem>emptyList(), null, Collections.<IndexItem>emptyList(), "CREATE TABLE t", stats);
        JsonNode json = mapper.readTree(mapper.writeValueAsString(present)).path("stats");
        assertThat(json.has("Rows")).isFalse();
        assertThat(json.has("Data_length")).isFalse();
        assertThat(json.path("engine").asText()).isEqualTo("InnoDB");
        assertThat(json.path("estimatedRows").asLong()).isEqualTo(12L);
        assertThat(json.path("dataBytes").asLong()).isEqualTo(100L);
        assertThat(json.path("indexBytes").asLong()).isEqualTo(20L);
        assertThat(json.path("autoIncrement").asLong()).isEqualTo(13L);
        assertThat(json.path("createTime").asText()).isEqualTo("2020-01-01T00:00:00Z");
        assertThat(json.path("updateTime").isNull()).isTrue();
        assertThat(json.path("comment").asText()).isEqualTo("订单");
    }
}
