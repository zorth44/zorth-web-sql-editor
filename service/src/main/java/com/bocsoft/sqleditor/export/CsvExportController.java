package com.bocsoft.sqleditor.export;

import com.bocsoft.sqleditor.auth.CurrentAuth;
import com.bocsoft.sqleditor.common.ClientIpResolver;
import com.bocsoft.sqleditor.common.RequestIds;
import com.bocsoft.sqleditor.export.api.SqlExportRequest;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1/sql/exports")
public class CsvExportController {
    private final CsvExportService service;
    private final ClientIpResolver clientIps;

    public CsvExportController(CsvExportService service, ClientIpResolver clientIps) {
        this.service = service;
        this.clientIps = clientIps;
    }

    @PostMapping(produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> export(@Valid @RequestBody SqlExportRequest request,
                                                        HttpServletRequest servlet) {
        CsvExportService.PreparedExport p = service.prepare(
            CurrentAuth.get(), request,
            String.valueOf(servlet.getAttribute(RequestIds.ATTRIBUTE)),
            clientIps.resolve(servlet));
        ContentDisposition disposition = ContentDisposition.attachment()
            .filename(p.getFilename(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
            .body(p.getBody());
    }
}
