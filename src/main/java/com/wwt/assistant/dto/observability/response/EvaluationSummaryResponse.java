package com.wwt.assistant.dto.observability.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class EvaluationSummaryResponse {
    private Integer positiveFeedbackCount;
    private Integer negativeFeedbackCount;
    private Double avgScore;
    private Double helpfulRate;
    private Integer effectiveAnswerCount;
}
