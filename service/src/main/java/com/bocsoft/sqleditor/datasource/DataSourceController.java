package com.bocsoft.sqleditor.datasource;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.datasource.api.ConnectionRequest;
import com.bocsoft.sqleditor.datasource.api.ConnectionTestResult;
import com.bocsoft.sqleditor.datasource.api.CreateDataSourceRequest;
import com.bocsoft.sqleditor.datasource.api.CursorPage;
import com.bocsoft.sqleditor.datasource.api.DataSourceDetailResponse;
import com.bocsoft.sqleditor.datasource.api.DataSourceListItemResponse;
import com.bocsoft.sqleditor.datasource.api.UpdateDataSourceRequest;
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
@RequestMapping("/api/v1")
public class DataSourceController {
    private final DataSourceService service;
    public DataSourceController(DataSourceService service){this.service=service;}

    @GetMapping("/data-sources")
    public CursorPage<DataSourceListItemResponse> list(@RequestParam(defaultValue="") String keyword,
        @RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) String pageToken){return service.list(auth(),keyword,pageSize,pageToken);}
    @GetMapping("/data-sources/{id}") public DataSourceDetailResponse get(@PathVariable String id){return service.get(auth(),id);}
    @PostMapping("/data-sources") public ResponseEntity<DataSourceDetailResponse> create(@Valid @RequestBody CreateDataSourceRequest request){DataSourceDetailResponse result=service.create(auth(),request);return ResponseEntity.created(URI.create("/api/v1/data-sources/"+result.getId())).body(result);}
    @PutMapping("/data-sources/{id}") public DataSourceDetailResponse update(@PathVariable String id,@Valid @RequestBody UpdateDataSourceRequest request){return service.update(auth(),id,request);}
    @DeleteMapping("/data-sources/{id}") public ResponseEntity<Void> delete(@PathVariable String id,@RequestParam long version){service.delete(auth(),id,version);return ResponseEntity.noContent().build();}
    @PostMapping("/data-sources:test") public ConnectionTestResult testUnsaved(@Valid @RequestBody ConnectionRequest request){return service.testUnsaved(request);}
    @PostMapping("/data-sources/{id}:test") public ConnectionTestResult testSavedOrEdits(@PathVariable String id,@Valid @RequestBody(required=false) ConnectionRequest request){return request==null?service.testSaved(auth(),id):service.testEdits(auth(),id,request);}
    private AuthContext auth(){return CurrentAuth.get();}
}
