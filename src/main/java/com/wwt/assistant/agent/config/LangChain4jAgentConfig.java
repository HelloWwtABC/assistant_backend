package com.wwt.assistant.agent.config;

import com.wwt.assistant.config.OllamaProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Planning Agent 的 LangChain4j 基础配置。
 */
@Configuration
public class LangChain4jAgentConfig {

    @Bean("planningAgentChatModel")
    public ChatModel planningAgentChatModel(OllamaProperties ollamaProperties) {
        if (!StringUtils.hasText(ollamaProperties.getModel())) {
            throw new IllegalStateException("ollama.model must not be blank for planning agent");
        }

        int timeoutSeconds = ollamaProperties.getTimeoutSeconds() == null || ollamaProperties.getTimeoutSeconds() <= 0
                ? 120
                : ollamaProperties.getTimeoutSeconds();

        OllamaChatModel.OllamaChatModelBuilder builder = OllamaChatModel.builder()
                .baseUrl(resolveBaseUrl(ollamaProperties))
                .modelName(ollamaProperties.getModel().trim())
                .timeout(Duration.ofSeconds(timeoutSeconds));

        if (ollamaProperties.getTemperature() != null) {
            builder.temperature(ollamaProperties.getTemperature());
        }
        if (ollamaProperties.getNumCtx() != null && ollamaProperties.getNumCtx() > 0) {
            builder.numCtx(ollamaProperties.getNumCtx());
        }

        return builder.build();
    }

    private String resolveBaseUrl(OllamaProperties ollamaProperties) {
        return StringUtils.hasText(ollamaProperties.getBaseUrl())
                ? ollamaProperties.getBaseUrl().trim()
                : "http://localhost:11434";
    }
}
