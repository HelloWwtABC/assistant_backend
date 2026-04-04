package com.wwt.assistant.dto.qa.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QaChatRequest {
    private String sessionId;
    private String question;
}
