package com.bocsoft.sqleditor.optimization;

import com.bocsoft.sqleditor.auth.AuthContext;
import com.bocsoft.sqleditor.optimization.api.OptimizationReport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class SqlLiteAgentClient {
    private final OptimizationProperties properties;
    private final ObjectMapper json;
    private final OptimizationReportValidator reports;
    private final ScheduledExecutorService deadlines = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "optimization-http-deadline"); t.setDaemon(true); return t;
    });
    public SqlLiteAgentClient(OptimizationProperties properties, ObjectMapper json, OptimizationReportValidator reports) {
        this.properties = properties; this.json = json; this.reports = reports;
    }
    public OptimizationReport analyze(String datasourceId, String database, String sql, String sessionId,
                                      String requestId, String token, AuthContext auth) {
        String message = message(datasourceId, database, sql);
        HttpURLConnection connection = null;
        ScheduledFuture<?> deadline = null;
        AtomicBoolean timedOut = new AtomicBoolean();
        try {
            Map<String, String> payload = new LinkedHashMap<String, String>();
            payload.put("sessionId", sessionId); payload.put("message", message);
            byte[] body = json.writeValueAsBytes(payload);
            String base = properties.getAgentBaseUrl().replaceAll("/+$", "");
            connection = (HttpURLConnection) new URL(base + "/api/v1/agents/sql-lite-optimizer/chat").openConnection();
            final HttpURLConnection active = connection;
            deadline = deadlines.schedule(() -> { timedOut.set(true); active.disconnect(); }, properties.getCallTimeoutMs(), TimeUnit.MILLISECONDS);
            connection.setRequestMethod("POST"); connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(properties.getConnectTimeoutMs()); connection.setReadTimeout(properties.getCallTimeoutMs());
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("X-Request-Id", requestId); connection.setRequestProperty("Content-Type", "application/json");
            if (properties.isDevelopmentUserHeader()) connection.setRequestProperty("X-User-Id", auth.getUserId());
            connection.setDoOutput(true);
            // Streaming mode prevents transparent replay of a sent POST for authentication/redirect/reconnect.
            connection.setFixedLengthStreamingMode(body.length);
            try (java.io.OutputStream out = connection.getOutputStream()) { out.write(body); }
            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new OptimizationFailure("AGENT_AUTH_FAILED");
            if (status < 200 || status >= 300) throw new OptimizationFailure("AGENT_UNAVAILABLE");
            if (connection.getContentLengthLong() > properties.getMaxReportBytes()) throw new OptimizationFailure("REPORT_TOO_LARGE");
            byte[] bytes;
            try (InputStream in = connection.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (timedOut.get()) throw new OptimizationFailure("AGENT_TIMEOUT");
                    if (out.size() + count > properties.getMaxReportBytes()) throw new OptimizationFailure("REPORT_TOO_LARGE");
                    out.write(buffer, 0, count);
                }
                bytes = out.toByteArray();
            }
            if (timedOut.get()) throw new OptimizationFailure("AGENT_TIMEOUT");
            JsonNode root;
            try { root = json.readTree(bytes); } catch (Exception e) { throw new OptimizationFailure("REPORT_INVALID"); }
            return reports.validate(root == null ? null : root.get("structuredOutput"));
        } catch (OptimizationFailure e) { throw e; }
        catch (SocketTimeoutException e) { throw new OptimizationFailure("AGENT_TIMEOUT"); }
        catch (Exception e) { throw new OptimizationFailure(timedOut.get() ? "AGENT_TIMEOUT" : "AGENT_UNAVAILABLE"); }
        finally { if (deadline != null) deadline.cancel(false); if (connection != null) connection.disconnect(); }
    }
    public static String message(String datasourceId, String database, String sql) {
        coordinate(datasourceId); coordinate(database);
        String message = "datasourceId: " + datasourceId + "\ndatabase: " + database + "\n" + sql;
        if (message.length() > 65536) throw new OptimizationFailure("REPORT_INVALID");
        return message;
    }
    private static void coordinate(String value) {
        if (value == null || value.trim().isEmpty() || value.codePoints().anyMatch(Character::isISOControl))
            throw new OptimizationFailure("REPORT_INVALID");
    }
    @PreDestroy public void close() { deadlines.shutdownNow(); }
}
