package com.bocsoft.sqleditor.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bocsoft.sqleditor.metadata.api.ConstraintColumns;
import com.bocsoft.sqleditor.metadata.api.ConstraintEvidence;
import com.bocsoft.sqleditor.metadata.api.MetadataCoverage;
import com.bocsoft.sqleditor.metadata.api.MetadataSection;
import com.bocsoft.sqleditor.metadata.api.RelationshipCardinality;
import com.bocsoft.sqleditor.metadata.api.RelationshipDirection;
import com.bocsoft.sqleditor.metadata.api.RelationshipEdge;
import com.bocsoft.sqleditor.metadata.api.UniqueKeyItem;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConstraintGroupingTest {
    @Test void groupsCompositeForeignKeyAsOneAtomicEdge() {
        List<ConstraintGrouping.ForeignKeySequenceRow> rows = Arrays.asList(
            row("fk_comp", "sales", "order_item", "order_id", "sales", "orders", "id", (short) 1),
            row("fk_comp", "sales", "order_item", "order_year", "sales", "orders", "year", (short) 2));
        List<RelationshipEdge> edges = ConstraintGrouping.groupForeignKeys(rows, "sales", "order_item",
            RelationshipDirection.OUTBOUND, Collections.singleton(ConstraintColumns.setKey(Arrays.asList("id"))), 10);
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).getSourceColumns()).containsExactly("order_id", "order_year");
        assertThat(edges.get(0).getTargetColumns()).containsExactly("id", "year");
        assertThat(edges.get(0).getConstraintName()).isEqualTo("fk_comp");
        assertThat(edges.get(0).getEvidence()).isEqualTo(ConstraintEvidence.FOREIGN_KEY);
        assertThat(edges.get(0).getDirection()).isEqualTo(RelationshipDirection.OUTBOUND);
    }

    @Test void sortsRelationshipsStablyAndDoesNotSplitCompositesWhenCropped() {
        List<ConstraintGrouping.ForeignKeySequenceRow> rows = Arrays.asList(
            row("fk_b", "sales", "child", "b_id", "sales", "bravo", "id", (short) 1),
            row("fk_a", "sales", "child", "a1", "sales", "alpha", "id", (short) 1),
            row("fk_a", "sales", "child", "a2", "sales", "alpha", "code", (short) 2));
        List<RelationshipEdge> edges = ConstraintGrouping.groupForeignKeys(rows, "sales", "child",
            RelationshipDirection.OUTBOUND, Collections.<String>emptySet(), 10);
        assertThat(edges).extracting(RelationshipEdge::getConstraintName).containsExactly("fk_a", "fk_b");
        assertThat(edges.get(0).getSourceColumns()).containsExactly("a1", "a2");
        MetadataSection<RelationshipEdge> cropped = ConstraintGrouping.crop(edges, 1);
        assertThat(cropped.getCoverage()).isEqualTo(MetadataCoverage.TRUNCATED);
        assertThat(cropped.getItems()).hasSize(1);
        assertThat(cropped.getItems().get(0).getSourceColumns()).containsExactly("a1", "a2");
        assertThat(cropped.getAppliedLimit()).isEqualTo(1);
    }

    @Test void emptyCompleteIsConfirmedAbsence() {
        MetadataSection<UniqueKeyItem> unique = ConstraintGrouping.uniqueKeys(
            Collections.<ConstraintGrouping.IndexSnapshot>emptyList(), Collections.<String>emptyList(), null,
            Collections.<String>emptySet(), 8);
        assertThat(unique.getCoverage()).isEqualTo(MetadataCoverage.COMPLETE);
        assertThat(unique.getItems()).isEmpty();
        assertThat(ConstraintGrouping.groupForeignKeys(Collections.<ConstraintGrouping.ForeignKeySequenceRow>emptyList(),
            "db", "t", RelationshipDirection.OUTBOUND, Collections.<String>emptySet(), 10)).isEmpty();
    }

    @Test void reportsUniqueIndexWhenConstraintIsNotProvenAndSkipsOrdinaryIndexes() {
        List<ConstraintGrouping.IndexSnapshot> indexes = Arrays.asList(
            new ConstraintGrouping.IndexSnapshot("PRIMARY", true, "OTHER", Collections.singletonList("id")),
            new ConstraintGrouping.IndexSnapshot("uk_email", true, "OTHER", Collections.singletonList("email")),
            new ConstraintGrouping.IndexSnapshot("ux_code", true, "OTHER", Collections.singletonList("code")),
            new ConstraintGrouping.IndexSnapshot("idx_email", false, "OTHER", Collections.singletonList("email")));
        Set<String> proven = new HashSet<String>(Collections.singletonList("uk_email"));
        MetadataSection<UniqueKeyItem> unique = ConstraintGrouping.uniqueKeys(indexes, Collections.singletonList("id"),
            "PRIMARY", proven, 8);
        assertThat(unique.getItems()).extracting(UniqueKeyItem::getName).containsExactly("uk_email", "ux_code");
        assertThat(unique.getItems()).extracting(UniqueKeyItem::getEvidence)
            .containsExactly(ConstraintEvidence.UNIQUE_CONSTRAINT, ConstraintEvidence.UNIQUE_INDEX);
    }

    @Test void dropsMalformedDriverRowsAndDoesNotFabricateConstraintNames() {
        List<ConstraintGrouping.ForeignKeySequenceRow> rows = Arrays.asList(
            row(null, "sales", "child", "a", "sales", "parent", "id", (short) 1),
            row(null, "sales", "child", null, "sales", "parent", "id", (short) 2),
            row("fk_ok", "sales", "child", "b", "sales", "other", "id", (short) 1),
            row("fk_bad", "sales", "child", "c", "sales", "gone", null, (short) 1));
        List<RelationshipEdge> edges = ConstraintGrouping.groupForeignKeys(rows, "sales", "child",
            RelationshipDirection.OUTBOUND, Collections.<String>emptySet(), 10);
        assertThat(edges).hasSize(2);
        assertThat(edges).extracting("constraintName").contains("fk_ok").containsNull();
        assertThat(edges).filteredOn("constraintName", (String) null)
            .extracting(RelationshipEdge::getSourceColumns)
            .containsExactly(Collections.singletonList("a"));
    }

    @Test void ordinaryIndexNeverBecomesARelationship() {
        assertThat(ConstraintGrouping.groupForeignKeys(Collections.<ConstraintGrouping.ForeignKeySequenceRow>emptyList(),
            "db", "t", RelationshipDirection.OUTBOUND, Collections.<String>emptySet(), 10)).isEmpty();
    }

    @Test void outboundCardinalityUsesRequestedTableUniqueness() {
        List<ConstraintGrouping.ForeignKeySequenceRow> rows = Collections.singletonList(
            row("fk", "sales", "child", "id", "sales", "parent", "id", (short) 1));
        Set<String> unique = Collections.singleton(ConstraintColumns.setKey(Collections.singletonList("id")));
        List<RelationshipEdge> oneToOne = ConstraintGrouping.groupForeignKeys(rows, "sales", "child",
            RelationshipDirection.OUTBOUND, unique, 10);
        assertThat(oneToOne.get(0).getCardinality()).isEqualTo(RelationshipCardinality.ONE_TO_ONE);
        List<RelationshipEdge> manyToOne = ConstraintGrouping.groupForeignKeys(rows, "sales", "child",
            RelationshipDirection.OUTBOUND, Collections.<String>emptySet(), 10);
        assertThat(manyToOne.get(0).getCardinality()).isEqualTo(RelationshipCardinality.MANY_TO_ONE);
        List<RelationshipEdge> inbound = ConstraintGrouping.groupForeignKeys(
            Collections.singletonList(row("fk", "sales", "child", "parent_id", "sales", "parent", "id", (short) 1)),
            "sales", "parent", RelationshipDirection.INBOUND, unique, 10);
        assertThat(inbound.get(0).getCardinality()).isEqualTo(RelationshipCardinality.ONE_TO_MANY);
    }

    @Test void rejectsBlankOrMismatchedCompositePairs() {
        assertThatThrownBy(() -> new UniqueKeyItem("uk", Collections.<String>emptyList(), ConstraintEvidence.UNIQUE_INDEX))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new com.bocsoft.sqleditor.metadata.api.ForeignKeyItem("fk",
            Collections.singletonList("a"), "db", "t", Arrays.asList("a", "b"), ConstraintEvidence.FOREIGN_KEY))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private ConstraintGrouping.ForeignKeySequenceRow row(String name, String fkNs, String fkTable, String fkCol,
                                                         String pkNs, String pkTable, String pkCol, short seq) {
        return new ConstraintGrouping.ForeignKeySequenceRow(name, fkNs, fkTable, fkCol, pkNs, pkTable, pkCol, seq);
    }
}
