package com.springchat.springchat_chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration(proxyBeanMethods = false)
public class WebSocketSchedulerConfig {
    @Bean(name = "webSocketTaskScheduler")
    TaskScheduler webSocketTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-expiry-");
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
