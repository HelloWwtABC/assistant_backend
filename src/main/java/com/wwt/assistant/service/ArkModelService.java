package com.wwt.assistant.service;

import com.wwt.assistant.dto.model.ModelChatResult;
import java.util.List;

public interface ArkModelService {
    List<List<Float>> createEmbeddings(List<String> texts);

    ModelChatResult chat(String systemPrompt, String userPrompt);
}
