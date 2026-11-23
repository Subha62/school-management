package com.schoolmanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Kept separate from {@link ApplicationConfig} so web-layer slice tests do not require JPA. */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}
