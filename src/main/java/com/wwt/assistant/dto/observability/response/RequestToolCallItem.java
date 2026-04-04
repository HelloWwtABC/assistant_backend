package com.wwt.assistant.dto.observability.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestToolCallItem {
    private String toolName;
    private String inputSummary;
    private String outputSummary;
    private String status;
    private Integer durationMs;
}
