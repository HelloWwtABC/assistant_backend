package com.wwt.assistant.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

@Configuration
@EnableAsync
@RequiredArgsConstructor
public class QdrantConfig {

    private final QdrantProperties qdrantProperties;

    @Bean(destroyMethod = "close")
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(
                qdrantProperties.getHost(),
                qdrantProperties.getPort(),
                qdrantProperties.isUseTls());
        if (StringUtils.hasText(qdrantProperties.getApiKey())) {
            builder.withApiKey(qdrantProperties.getApiKey());
        }
        return new QdrantClient(builder.build());
    }

    @Bean("documentProcessExecutor")
    public Executor documentProcessExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("document-process-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }

    @Bean("documentVectorExecutor")
    public Executor documentVectorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("document-vector-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.initialize();
        return executor;
    }
}
