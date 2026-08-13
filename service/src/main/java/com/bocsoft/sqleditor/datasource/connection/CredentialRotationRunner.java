package com.bocsoft.sqleditor.datasource.connection;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CredentialRotationRunner implements ApplicationRunner {
    private static final Logger LOG=LoggerFactory.getLogger(CredentialRotationRunner.class);
    private final CredentialRotationService service;private final SqlEditorProperties properties;
    public CredentialRotationRunner(CredentialRotationService service,SqlEditorProperties properties){this.service=service;this.properties=properties;}
    @Override public void run(ApplicationArguments args){SqlEditorProperties.Rotation rotation=properties.getCredentials().getRotation();if(!rotation.isEnabled())return;if(!StringUtils.hasText(rotation.getFromVersion()))throw new IllegalStateException("Rotation from-version is required");CredentialRotationService.RotationResult result=service.rotate(rotation.getFromVersion(),rotation.isDryRun(),rotation.getBatchSize());LOG.info("Credential rotation finished: rotated={}, remaining={}",result.getRotated(),result.getRemaining());}
}
