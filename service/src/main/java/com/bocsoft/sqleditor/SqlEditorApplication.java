package com.bocsoft.sqleditor;

import com.bocsoft.sqleditor.config.SqlEditorProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan({"com.bocsoft.sqleditor.datasource.persistence", "com.bocsoft.sqleditor.history.persistence"})
@EnableConfigurationProperties(SqlEditorProperties.class)
@EnableScheduling
public class SqlEditorApplication {
    public static void main(String[] args) {
        SpringApplication.run(SqlEditorApplication.class, args);
    }
}
