package com.wwt.assistant.dto.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ModelChatResult {
    private String content;
    private String modelName;
    private Integer promptTokens;
    private Integer completionTokens;
}
