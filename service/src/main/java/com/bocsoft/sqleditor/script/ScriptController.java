package com.bocsoft.sqleditor.script;

import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.script.api.ScriptDetail;
import com.bocsoft.sqleditor.script.api.ScriptSummary;
import com.bocsoft.sqleditor.script.api.ScriptWriteRequest;
import java.net.URI;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sql/scripts")
public class ScriptController {
    private final ScriptService service;
    public ScriptController(ScriptService service) { this.service = service; }

    @GetMapping
    public CursorPage<ScriptSummary> list(@RequestParam(defaultValue = "") String keyword,
        @RequestParam(required = false) String dataSourceId, @RequestParam(required = false) String database,
        @RequestParam(defaultValue = "30") int pageSize, @RequestParam(required = false) String pageToken) {
        return service.list(CurrentAuth.get(), keyword, dataSourceId, database, pageSize, pageToken);
    }

    @GetMapping("/{id}")
    public ScriptDetail get(@PathVariable String id) {
        return service.get(CurrentAuth.get(), id);
    }

    @PostMapping
    public ResponseEntity<ScriptDetail> create(@Valid @RequestBody ScriptWriteRequest request) {
        ScriptDetail result = service.create(CurrentAuth.get(), request);
        return ResponseEntity.created(URI.create("/api/v1/sql/scripts/" + result.getId())).body(result);
    }

    @PutMapping("/{id}")
    public ScriptDetail update(@PathVariable String id, @Valid @RequestBody ScriptWriteRequest request) {
        return service.update(CurrentAuth.get(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, @RequestParam long version) {
        service.delete(CurrentAuth.get(), id, version);
        return ResponseEntity.noContent().build();
    }
}
