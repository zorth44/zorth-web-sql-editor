package com.bocsoft.sqleditor.agentapi;

import com.bocsoft.sqleditor.agentapi.api.AgentColumnItem;
import com.bocsoft.sqleditor.agentapi.api.AgentDataSourceDetail;
import com.bocsoft.sqleditor.agentapi.api.AgentDataSourceSummary;
import com.bocsoft.sqleditor.agentapi.api.AgentDatabaseItem;
import com.bocsoft.sqleditor.agentapi.api.AgentTableDetail;
import com.bocsoft.sqleditor.agentapi.api.AgentTableItem;
import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.datasource.DataSourceService;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.metadata.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1/agent")
@Tag(name = "Agent Database API", description = "Versioned read-only database capabilities for Agent consumers")
public class AgentMetadataController {
    private final DataSourceService dataSources;
    private final MetadataService metadata;
    private final AgentApiMapper mapper;

    public AgentMetadataController(DataSourceService dataSources, MetadataService metadata, AgentApiMapper mapper) {
        this.dataSources = dataSources;
        this.metadata = metadata;
        this.mapper = mapper;
    }

    @GetMapping("/data-sources")
    @Operation(summary = "List visible datasources for the current user product")
    public CursorPage<AgentDataSourceSummary> list(@RequestParam(defaultValue = "") String keyword,
                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                   @RequestParam(required = false) String pageToken) {
        return mapper.summaries(dataSources.list(CurrentAuth.get(), keyword, pageSize, pageToken));
    }

    @GetMapping("/data-sources/{id}")
    @Operation(summary = "Get a visible datasource without credentials or JDBC properties")
    public AgentDataSourceDetail get(@PathVariable String id) {
        return mapper.detail(dataSources.get(CurrentAuth.get(), id));
    }

    @GetMapping("/data-sources/{id}/databases")
    @Operation(summary = "List NAMESPACE items (MySQL catalogs or PostgreSQL schemas) for a visible datasource")
    public CursorPage<AgentDatabaseItem> databases(@PathVariable String id,
                                                   @RequestParam(defaultValue = "") String keyword,
                                                   @RequestParam(defaultValue = "100") int pageSize,
                                                   @RequestParam(required = false) String pageToken,
                                                   @RequestParam(defaultValue = "false") boolean includeSystem) {
        return mapper.databases(metadata.databases(CurrentAuth.get(), id, keyword, pageSize, pageToken, includeSystem));
    }

    @GetMapping("/data-sources/{id}/tables")
    @Operation(summary = "Search tables in a namespace")
    public CursorPage<AgentTableItem> tables(@PathVariable String id, @RequestParam String database,
                                             @RequestParam(defaultValue = "") String keyword,
                                             @RequestParam(defaultValue = "TABLE,VIEW") String types,
                                             @RequestParam(defaultValue = "50") int pageSize,
                                             @RequestParam(required = false) String pageToken) {
        return mapper.tables(metadata.tables(CurrentAuth.get(), id, database, keyword, types, pageSize, pageToken));
    }

    @GetMapping("/data-sources/{id}/columns")
    @Operation(summary = "Search columns across tables in a namespace")
    public CursorPage<AgentColumnItem> columns(@PathVariable String id, @RequestParam String database,
                                               @RequestParam String keyword,
                                               @RequestParam(defaultValue = "50") int pageSize,
                                               @RequestParam(required = false) String pageToken) {
        return mapper.columns(metadata.columns(CurrentAuth.get(), id, database, keyword, pageSize, pageToken));
    }

    @GetMapping("/data-sources/{id}/table-detail")
    @Operation(summary = "Return normalized table detail including optional DDL and statistics")
    public AgentTableDetail tableDetail(@PathVariable String id, @RequestParam String database,
                                        @RequestParam String table) {
        return mapper.tableDetail(metadata.detail(CurrentAuth.get(), id, database, table));
    }
}
