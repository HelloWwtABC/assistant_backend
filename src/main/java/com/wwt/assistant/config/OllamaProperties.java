package com.wwt.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {

    private String baseUrl = "http://localhost:11434";

    private String model;

    private Integer timeoutSeconds = 120;

    private Double temperature = 0.5D;

    private Integer numCtx = 4096;

    private Boolean stream = false;
}
