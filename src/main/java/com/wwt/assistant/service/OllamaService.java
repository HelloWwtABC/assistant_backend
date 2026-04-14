package com.wwt.assistant.service;

import com.wwt.assistant.dto.model.ModelChatResult;

public interface OllamaService {

    ModelChatResult chat(String systemPrompt, String userPrompt);

    boolean modelExists();

    void pullModel();
}
