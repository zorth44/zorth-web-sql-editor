package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.agentapi.api.AgentColumnItem;
import com.bocsoft.sqleditor.agentapi.api.AgentDataSourceDetail;
import com.bocsoft.sqleditor.agentapi.api.AgentDataSourceSummary;
import com.bocsoft.sqleditor.agentapi.api.AgentDatabaseItem;
import com.bocsoft.sqleditor.agentapi.api.AgentFinding;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlExplainResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentSqlValidateResponse;
import com.bocsoft.sqleditor.agentapi.api.AgentRelationshipPage;
import com.bocsoft.sqleditor.agentapi.api.AgentSectionCoverage;
import com.bocsoft.sqleditor.agentapi.api.AgentTableCoverage;
import com.bocsoft.sqleditor.agentapi.api.AgentTableLimits;
import com.bocsoft.sqleditor.agentapi.api.AgentTableColumn;
import com.bocsoft.sqleditor.agentapi.api.AgentTableDetail;
import com.bocsoft.sqleditor.agentapi.api.AgentTableItem;
import com.bocsoft.sqleditor.agentapi.api.AgentTableRef;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.api.DataSourceDetailResponse;
import com.bocsoft.sqleditor.datasource.api.DataSourceListItemResponse;
import com.bocsoft.sqleditor.engine.PlanEvidence;
import com.bocsoft.sqleditor.engine.PlanFinding;
import com.bocsoft.sqleditor.execution.SqlFinding;
import com.bocsoft.sqleditor.execution.SqlSafetyAssessment;
import com.bocsoft.sqleditor.execution.SqlTableRef;
import com.bocsoft.sqleditor.metadata.api.ColumnItem;
import com.bocsoft.sqleditor.metadata.api.ColumnSearchItem;
import com.bocsoft.sqleditor.metadata.api.DatabaseItem;
import com.bocsoft.sqleditor.metadata.api.ForeignKeyItem;
import com.bocsoft.sqleditor.metadata.api.IndexItem;
import com.bocsoft.sqleditor.metadata.api.MetadataCoverage;
import com.bocsoft.sqleditor.metadata.api.MetadataSection;
import com.bocsoft.sqleditor.metadata.api.RelationshipPage;
import com.bocsoft.sqleditor.metadata.api.TableDetailResponse;
import com.bocsoft.sqleditor.metadata.api.TableItem;
import com.bocsoft.sqleditor.metadata.api.TableMetadata;
import com.bocsoft.sqleditor.metadata.api.UniqueKeyItem;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class AgentApiMapper {
    public AgentDataSourceSummary summary(DataSourceListItemResponse source) {
        AgentDataSourceSummary out = new AgentDataSourceSummary();
        copy(source, out);
        return out;
    }

    public AgentDataSourceDetail detail(DataSourceDetailResponse source) {
        AgentDataSourceDetail out = new AgentDataSourceDetail();
        copy(source, out);
        out.setConnectTimeoutSeconds(source.getConnectTimeoutSeconds());
        out.setDescription(source.getDescription());
        return out;
    }

    public CursorPage<AgentDataSourceSummary> summaries(CursorPage<DataSourceListItemResponse> page) {
        List<AgentDataSourceSummary> items = new ArrayList<AgentDataSourceSummary>();
        for (DataSourceListItemResponse item : page.getItems()) items.add(summary(item));
        return new CursorPage<AgentDataSourceSummary>(items, page.getNextPageToken());
    }

    public CursorPage<AgentDatabaseItem> databases(CursorPage<DatabaseItem> page) {
        List<AgentDatabaseItem> items = new ArrayList<AgentDatabaseItem>();
        for (DatabaseItem item : page.getItems()) {
            items.add(new AgentDatabaseItem(item.getName(), item.getKind()));
        }
        return new CursorPage<AgentDatabaseItem>(items, page.getNextPageToken());
    }

    public CursorPage<AgentTableItem> tables(CursorPage<TableItem> page) {
        List<AgentTableItem> items = new ArrayList<AgentTableItem>();
        for (TableItem item : page.getItems()) {
            items.add(new AgentTableItem(item.getDatabase(), item.getName(), item.getType(), item.getComment()));
        }
        return new CursorPage<AgentTableItem>(items, page.getNextPageToken());
    }

    public CursorPage<AgentColumnItem> columns(CursorPage<ColumnSearchItem> page) {
        List<AgentColumnItem> items = new ArrayList<AgentColumnItem>();
        for (ColumnSearchItem item : page.getItems()) {
            items.add(new AgentColumnItem(item.getDatabase(), item.getTable(), item.getName(),
                item.getJdbcType(), item.getTypeName(), item.isNullable(), item.getComment()));
        }
        return new CursorPage<AgentColumnItem>(items, page.getNextPageToken());
    }

    public AgentTableDetail tableDetail(TableMetadata source) {
        TableDetailResponse detail = source.getDetail();
        List<AgentTableColumn> columns = new ArrayList<AgentTableColumn>();
        if (detail.getColumns() != null) {
            for (ColumnItem column : detail.getColumns()) {
                columns.add(new AgentTableColumn(column.getName(), column.getJdbcType(), column.getTypeName(),
                    column.getLength(), column.getPrecision(), column.getScale(), column.isNullable(),
                    column.isPrimaryKey(), column.getDefaultValue(), column.getExtra(), column.getComment(),
                    column.getOrdinal()));
            }
        }
        MetadataSection<UniqueKeyItem> uniqueKeys = source.getConstraints().getUniqueKeys();
        MetadataSection<ForeignKeyItem> foreignKeys = source.getConstraints().getForeignKeys();
        MetadataSection<IndexItem> indexes = source.getConstraints().getIndexes();
        return new AgentTableDetail(detail.getDatabase(), detail.getTable(), columns, detail.getPrimaryKey(),
            uniqueKeys.getItems(), foreignKeys.getItems(), indexes.getItems(), null, null,
            new AgentTableCoverage(section(uniqueKeys), section(foreignKeys), section(indexes)),
            new AgentTableLimits(uniqueKeys.getAppliedLimit(), foreignKeys.getAppliedLimit(), indexes.getAppliedLimit()));
    }

    public AgentRelationshipPage relationships(RelationshipPage page) {
        return new AgentRelationshipPage(page.getItems(), page.getNextPageToken(), page.getCoverage(), page.getAppliedLimit());
    }

    private AgentSectionCoverage section(MetadataSection<?> section) {
        if (section == null || section.getCoverage() == null) {
            return new AgentSectionCoverage(MetadataCoverage.UNAVAILABLE, null);
        }
        return new AgentSectionCoverage(section.getCoverage(), section.getAppliedLimit());
    }

    public AgentSqlValidateResponse validation(SqlSafetyAssessment assessment) {
        return new AgentSqlValidateResponse(assessment.isValid(), assessment.getStatementType(),
            assessment.isReadOnly(), assessment.isMultiStatement(), tableRefs(assessment.getTables()),
            findings(assessment.getWarnings()), findings(assessment.getViolations()));
    }

    public AgentSqlExplainResponse explain(PlanEvidence evidence) {
        List<AgentFinding> findings = new ArrayList<AgentFinding>();
        if (evidence.getFindings() != null) {
            for (PlanFinding finding : evidence.getFindings()) {
                findings.add(new AgentFinding(finding.getCode(), finding.getMessage()));
            }
        }
        return new AgentSqlExplainResponse(evidence.isSupported(), evidence.getEstimatedRows(), evidence.getFullScan(),
            evidence.getUsedIndexes(), evidence.getTables(), findings, evidence.getReason(),
            AgentExplainRisk.from(evidence));
    }

    private List<AgentTableRef> tableRefs(List<SqlTableRef> tables) {
        List<AgentTableRef> out = new ArrayList<AgentTableRef>();
        if (tables == null) return out;
        for (SqlTableRef table : tables) out.add(new AgentTableRef(table.getCatalog(), table.getSchema(), table.getName()));
        return out;
    }

    private List<AgentFinding> findings(List<SqlFinding> items) {
        List<AgentFinding> out = new ArrayList<AgentFinding>();
        if (items == null) return out;
        for (SqlFinding item : items) out.add(new AgentFinding(item.getCode(), item.getMessage()));
        return out;
    }

    private void copy(DataSourceListItemResponse source, AgentDataSourceSummary target) {
        target.setId(source.getId());
        target.setName(source.getName());
        target.setEngine(source.getEngine());
        target.setHost(source.getHost());
        target.setPort(source.getPort());
        target.setDefaultDatabase(source.getDefaultDatabase());
        target.setSslMode(source.getSslMode());
        target.setLastTestStatus(source.getLastTestStatus());
        target.setLastTestAt(source.getLastTestAt());
    }
}
