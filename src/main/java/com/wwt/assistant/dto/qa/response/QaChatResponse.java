package com.wwt.assistant.dto.qa.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QaChatResponse {
    private String sessionId;
    private QaMessageItem userMessage;
    private QaMessageItem assistantMessage;
    private QaSessionItem updatedSession;
}
