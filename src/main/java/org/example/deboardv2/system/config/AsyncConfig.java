package org.example.deboardv2.system.config;


import org.example.deboardv2.system.monitor.sechdulerMonitor.ContextCopyTaskDecorator;
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
        executor.setCorePoolSize(6);    // 평소에는 적은 스레드로 자원 절약
        executor.setMaxPoolSize(20);    // 트래픽 급증 시 빠르게 확장 가능
        executor.setQueueCapacity(200); // 대기열을 얼마나 세울지
        executor.setTaskDecorator(new ContextCopyTaskDecorator());
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
