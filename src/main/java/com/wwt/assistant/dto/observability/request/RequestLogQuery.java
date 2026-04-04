package com.wwt.assistant.dto.observability.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestLogQuery {
    private String keyword;
    private String status;
    private String usedTool;
    private String startDate;
    private String endDate;
    private Long page;
    private Long pageSize;
}
