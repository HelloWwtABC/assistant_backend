package com.wwt.assistant.dto.qa.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClearContextRequest {
    private String sessionId;
}
