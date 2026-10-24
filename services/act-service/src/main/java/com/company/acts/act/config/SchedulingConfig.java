package com.company.acts.act.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Включает фоновые задачи (очистка аннулированных актов). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}
