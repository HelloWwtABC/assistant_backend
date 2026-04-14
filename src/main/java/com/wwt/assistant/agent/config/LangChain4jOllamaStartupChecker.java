package com.wwt.assistant.agent.config;

import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 在启动后验证 LangChain4j 是否能够调用本地 Ollama。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LangChain4jOllamaStartupChecker {

    @Qualifier("planningAgentChatModel")
    private final ChatModel planningAgentChatModel;

    private final PlanningAgentLangChainProperties planningAgentLangChainProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void verifyOllamaConnectivity() {
        if (!planningAgentLangChainProperties.isStartupCheckEnabled()) {
            log.info("Skip LangChain4j Ollama startup check because agent.langchain4j.startup-check-enabled=false");
            return;
        }

        String prompt = StringUtils.hasText(planningAgentLangChainProperties.getStartupCheckPrompt())
                ? planningAgentLangChainProperties.getStartupCheckPrompt().trim()
                : "Reply with OK only.";
        try {
            String response = planningAgentChatModel.chat(prompt);
            log.info("LangChain4j Ollama startup check succeeded, response={}", abbreviate(response));
        } catch (Exception ex) {
            log.error("LangChain4j Ollama startup check failed", ex);
        }
    }

    private String abbreviate(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 120);
    }
}
