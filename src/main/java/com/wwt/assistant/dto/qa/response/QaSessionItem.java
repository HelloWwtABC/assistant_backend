package com.wwt.assistant.dto.qa.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QaSessionItem {
    private String sessionId;
    private String title;
    private String createdAt;
    private String updatedAt;
    private String lastMessagePreview;
    private Integer messageCount;
}
