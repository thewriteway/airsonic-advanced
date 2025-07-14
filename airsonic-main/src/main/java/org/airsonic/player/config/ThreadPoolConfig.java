package org.airsonic.player.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "BroadcastThreadPool")
    public ThreadPoolTaskExecutor configThreadPool() {
        var threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(2);
        threadPool.setMaxPoolSize(5);
        threadPool.setQueueCapacity(500);
        threadPool.setDaemon(true);
        threadPool.setThreadNamePrefix("BroadcastThread-");
        threadPool.initialize();
        return threadPool;
    }

    @Bean(name = "PodcastDownloadThreadPool")
    public ThreadPoolTaskExecutor podcastDownloadThreadPool() {
        var threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(2);
        threadPool.setMaxPoolSize(3);
        threadPool.setQueueCapacity(500);
        threadPool.setDaemon(true);
        threadPool.setThreadNamePrefix("podcast-download");
        threadPool.initialize();
        return threadPool;
    }

    @Bean(name = "PodcastRefreshThreadPool")
    public ThreadPoolTaskExecutor podcastRefreshThreadPool() {
        var threadPool = new ThreadPoolTaskExecutor();
        threadPool.setCorePoolSize(2);
        threadPool.setMaxPoolSize(5);
        threadPool.setQueueCapacity(1000);
        threadPool.setDaemon(true);
        threadPool.setThreadNamePrefix("podcast-refresh");
        threadPool.initialize();
        return threadPool;
    }
}
