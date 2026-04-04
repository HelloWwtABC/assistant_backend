package com.wwt.assistant.dto.observability.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestEvaluationItem {
    private Integer userScore;
    private Boolean helpful;
    private String remark;
    private String qualityLevel;
}
