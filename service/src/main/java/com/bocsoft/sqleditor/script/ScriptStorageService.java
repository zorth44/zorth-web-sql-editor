package com.bocsoft.sqleditor.script;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ScriptStorageService {
    private static final Logger log = LoggerFactory.getLogger(ScriptStorageService.class);
    private final Path basePath;

    public ScriptStorageService(SqlEditorProperties properties) {
        this.basePath = Paths.get(properties.getScripts().getStoragePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.basePath);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create script storage directory: " + this.basePath, e);
        }
    }

    public Path getScriptPath(String userId, String scriptId) {
        return basePath.resolve(userId).resolve(scriptId + ".sql");
    }

    public String readScript(String userId, String scriptId) {
        Path path = getScriptPath(userId, scriptId);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read script file: {}", path, e);
            throw new ScriptStorageException("Failed to read script file", e);
        }
    }

    public void writeScript(String userId, String scriptId, String content) {
        Path path = getScriptPath(userId, scriptId);
        try {
            Files.createDirectories(path.getParent());
            Path tempFile = Files.createTempFile(path.getParent(), ".tmp_", ".sql");
            try {
                Files.write(tempFile, content.getBytes(StandardCharsets.UTF_8));
                Files.move(tempFile, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.deleteIfExists(tempFile);
                throw e;
            }
        } catch (IOException e) {
            log.error("Failed to write script file: {}", path, e);
            throw new ScriptStorageException("Failed to write script file", e);
        }
    }

    public void deleteScript(String userId, String scriptId) {
        Path path = getScriptPath(userId, scriptId);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete script file: {}", path, e);
        }
    }

    public static class ScriptStorageException extends RuntimeException {
        public ScriptStorageException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
