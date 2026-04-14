package com.wwt.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "qdrant")
public class QdrantProperties {

    private String host = "192.168.74.132";

    private int port = 6334;

    private String collectionName = "kb_chunk_vectors";

    private int vectorSize = 2560;

    private String apiKey;

    private boolean useTls = false;
}
