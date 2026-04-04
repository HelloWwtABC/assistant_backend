package com.wwt.assistant.dto.session.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SessionRecordItem {
    private String sessionId;
    private String title;
    private Integer questionCount;
    private String knowledgeBaseName;
    private String knowledgeBaseId;
    private String lastQuestionSnippet;
    private String createdAt;
    private String updatedAt;
}
