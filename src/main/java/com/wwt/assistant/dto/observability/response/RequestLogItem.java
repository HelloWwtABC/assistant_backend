package com.wwt.assistant.dto.observability.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestLogItem {
    private String requestId;
    private String sessionId;
    private String questionSummary;
    private String status;
    private Integer latencyMs;
    private Integer hitChunkCount;
    private Integer citationCount;
    private Boolean usedTool;
    private String createdAt;
}
