package com.example.core.aiservice;

import com.example.core.grpc.v1.AiServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AiServiceClientConfig {

    @Bean(destroyMethod = "shutdownNow")
    ManagedChannel aiServiceChannel(@Value("${ai-service.target}") String target) {
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }

    @Bean
    AiServiceGrpc.AiServiceStub aiServiceStub(ManagedChannel aiServiceChannel) {
        return AiServiceGrpc.newStub(aiServiceChannel);
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService llmExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
