package com.wwt.assistant.dto.observability.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MetricsOverviewResponse {
    private Integer todayRequestCount;
    private Double avgLatencyMs;
    private Integer toolCallCount;
    private Double errorRate;
    private Double avgHitChunkCount;
    private Double citationCoverage;
}
