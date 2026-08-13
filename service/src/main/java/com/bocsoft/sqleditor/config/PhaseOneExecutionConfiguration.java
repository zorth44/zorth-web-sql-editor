package com.bocsoft.sqleditor.config;

import com.bocsoft.sqleditor.datasource.RunningTaskCounter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PhaseOneExecutionConfiguration {
    @Bean
    public RunningTaskCounter runningTaskCounter() { return dataSourceId -> 0; }
}
