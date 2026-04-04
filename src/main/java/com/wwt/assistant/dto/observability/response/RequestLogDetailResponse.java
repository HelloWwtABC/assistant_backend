package com.wwt.assistant.dto.observability.response;

import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RequestLogDetailResponse extends RequestLogItem {
    private String modelName;
    private String question;
    private String answer;
    private List<RequestCitationItem> citations;
    private List<RequestToolCallItem> toolCalls;
    private RequestEvaluationItem evaluation;
}
