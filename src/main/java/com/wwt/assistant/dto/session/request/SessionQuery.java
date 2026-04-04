package com.wwt.assistant.dto.session.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SessionQuery {
    private String keyword;
    private String knowledgeBaseId;
    private String startDate;
    private String endDate;
    private Long page;
    private Long pageSize;
}
