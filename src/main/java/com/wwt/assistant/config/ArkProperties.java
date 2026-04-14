package com.wwt.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "ark")
public class ArkProperties {

    private String apiKey;

    private String embeddingModel;

    private String chatModel;
}
