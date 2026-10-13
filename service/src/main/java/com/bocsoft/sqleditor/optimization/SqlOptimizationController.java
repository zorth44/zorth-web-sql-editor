package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.common.RequestIds;
import com.bocsoft.sqleditor.optimization.api.*;
import java.net.URI;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sql-optimization/tasks")
public class SqlOptimizationController {
    private final SqlOptimizationService service;
    public SqlOptimizationController(SqlOptimizationService service){this.service=service;}
    @PostMapping public ResponseEntity<AcceptedTask> create(@RequestBody CreateTaskRequest body,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest request){
        return accepted(service.create(CurrentAuth.get(),body,key,token(request),requestId(request)));
    }
    @GetMapping public TaskListResponse list(@RequestParam(required=false) String status,@RequestParam(defaultValue="20") String pageSize,@RequestParam(required=false) String pageToken){return service.list(CurrentAuth.get(),status,pageSize(pageSize),pageToken);}
    @GetMapping("/{id}") public TaskDetail detail(@PathVariable String id){return service.detail(CurrentAuth.get(),id);}
    @PostMapping("/{id}/retry") public ResponseEntity<AcceptedTask> retry(@PathVariable String id,@RequestHeader(value="Idempotency-Key",required=false) String key,HttpServletRequest request){
        return accepted(service.retry(CurrentAuth.get(),id,key,token(request),requestId(request)));
    }
    private int pageSize(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException e) { throw com.bocsoft.sqleditor.common.ApiException.validation("pageSize","INVALID","每页数量必须为整数"); }
    }
    private ResponseEntity<AcceptedTask> accepted(AcceptedTask task){return ResponseEntity.accepted().location(URI.create("/api/v1/sql-optimization/tasks/"+task.getId())).body(task);}
    private String token(HttpServletRequest request){return request.getHeader("Authorization").substring(7);}
    private String requestId(HttpServletRequest request){return String.valueOf(request.getAttribute(RequestIds.ATTRIBUTE));}
}
