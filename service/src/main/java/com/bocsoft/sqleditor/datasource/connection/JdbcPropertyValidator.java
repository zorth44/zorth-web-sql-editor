package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.common.ApiException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JdbcPropertyValidator {
    public Map<String, String> validate(Map<String, String> input) {
        Map<String, String> safe = new LinkedHashMap<String, String>();
        if (input == null) return safe;
        for (Map.Entry<String, String> entry : input.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if ("serverTimezone".equals(key)) validateTimeZone(value);
            else if ("characterSetResults".equals(key)) oneOf(key, value, "utf8", "UTF-8");
            else if ("zeroDateTimeBehavior".equals(key)) oneOf(key, value, "CONVERT_TO_NULL", "EXCEPTION", "ROUND");
            else if ("tinyInt1isBit".equals(key) || "sendFractionalSeconds".equals(key)) oneOf(key, value, "true", "false");
            else throw invalid(key);
            safe.put(key, value);
        }
        return safe;
    }

    private void validateTimeZone(String value) {
        try {
            if (value == null || value.length() > 64) throw new DateTimeException("invalid");
            ZoneId.of(value);
        } catch (DateTimeException exception) { throw invalid("serverTimezone"); }
    }
    private void oneOf(String key, String value, String... allowed) {
        if (!Arrays.asList(allowed).contains(value)) throw invalid(key);
    }
    private ApiException invalid(String key) {
        return ApiException.validation("properties", "NOT_ALLOWED", "JDBC 参数 " + key + " 不在白名单中");
    }
}
