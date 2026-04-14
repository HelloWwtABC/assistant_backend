package com.wwt.assistant.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import com.wwt.assistant.service.OllamaService;

@Slf4j
@Component
@RequiredArgsConstructor
public class OllamaModelInitializer {

    private final OllamaProperties ollamaProperties;
    private final OllamaService ollamaService;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeModel() {
        if (!StringUtils.hasText(ollamaProperties.getModel())) {
            log.warn("Skip Ollama model initialization because ollama.model is blank");
            return;
        }

        try {
            if (ollamaService.modelExists()) {
                log.info("Ollama model is ready: {}", ollamaProperties.getModel());
                return;
            }

            ollamaService.pullModel();
            log.info("Ollama model pulled successfully: {}", ollamaProperties.getModel());
        } catch (Exception ex) {
            log.error(
                    "Failed to initialize Ollama model, baseUrl={}, model={}",
                    ollamaProperties.getBaseUrl(),
                    ollamaProperties.getModel(),
                    ex);
        }
    }

}
