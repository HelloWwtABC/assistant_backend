package com.wwt.assistant.dto.tool.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ToolCallRecordItem {
    private String recordId;
    private String toolName;
    private String requestSummary;
    private String resultSummary;
    private String status;
    private Integer durationMs;
    private String calledAt;
}
