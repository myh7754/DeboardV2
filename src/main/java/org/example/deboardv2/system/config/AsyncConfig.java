package org.example.deboardv2.system.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "rssTaskExcutor")
    public Executor rssTaskExcutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 권장값 (환경에 따라 조정)
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("rss-exec-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "mailTaskExcutor")
    public Executor mailTaskExcutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("mail-exec-");
        executor.initialize();
        return executor;
    }
}
