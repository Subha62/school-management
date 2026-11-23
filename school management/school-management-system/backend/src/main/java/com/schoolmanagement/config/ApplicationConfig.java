package com.schoolmanagement.config;

import java.time.Clock;
import com.schoolmanagement.file.UploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, PaginationProperties.class, AppSecurityProperties.class,
        AttendanceProperties.class, UploadProperties.class, FeeProperties.class})
@EnableAsync
@EnableScheduling
public class ApplicationConfig {

    /** All time-dependent logic uses this clock so that it is testable and always UTC. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
