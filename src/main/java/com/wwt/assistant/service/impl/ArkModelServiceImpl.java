package com.wwt.assistant.service.impl;

import com.volcengine.ark.runtime.model.Usage;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionChoice;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionRequest;
import com.volcengine.ark.runtime.model.completion.chat.ChatCompletionResult;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessage;
import com.volcengine.ark.runtime.model.completion.chat.ChatMessageRole;
import com.volcengine.ark.runtime.model.embeddings.Embedding;
import com.volcengine.ark.runtime.model.embeddings.EmbeddingRequest;
import com.volcengine.ark.runtime.model.embeddings.EmbeddingResult;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbedding;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingInput;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingRequest;
import com.volcengine.ark.runtime.model.multimodalembeddings.MultimodalEmbeddingResult;
import com.volcengine.ark.runtime.service.ArkService;
import com.wwt.assistant.config.ArkProperties;
import com.wwt.assistant.dto.model.ModelChatResult;
import com.wwt.assistant.service.ArkModelService;
import jakarta.annotation.PreDestroy;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
public class ArkModelServiceImpl implements ArkModelService {

    private static final String TEXT_PART_TYPE = "text";

    private final ArkProperties arkProperties;

    private volatile ArkService arkService;

    public ArkModelServiceImpl(ArkProperties arkProperties) {
        this.arkProperties = arkProperties;
    }

    @Override
    public List<List<Float>> createEmbeddings(List<String> texts) {
        Assert.notEmpty(texts, "texts must not be empty");
        Assert.hasText(arkProperties.getApiKey(), "ark.api-key must not be blank");
        Assert.hasText(arkProperties.getEmbeddingModel(), "ark.embedding-model must not be blank");

        String model = arkProperties.getEmbeddingModel().trim();
        if (isMultiModalEmbeddingModel(model)) {
            return texts.stream()
                    .map(this::createMultiModalEmbedding)
                    .toList();
        }

        EmbeddingRequest request = EmbeddingRequest.builder()
                .model(model)
                .input(texts)
                .build();
        EmbeddingResult result = getArkService().createEmbeddings(request);
        if (result == null || result.getData() == null || result.getData().size() != texts.size()) {
            throw new IllegalStateException("Ark embedding result size does not match input size");
        }

        return result.getData().stream()
                .sorted(Comparator.comparing(Embedding::getIndex, Comparator.nullsLast(Integer::compareTo)))
                .map(this::toFloatVector)
                .toList();
    }

    @Override
    public ModelChatResult chat(String systemPrompt, String userPrompt) {
        Assert.hasText(systemPrompt, "systemPrompt must not be blank");
        Assert.hasText(userPrompt, "userPrompt must not be blank");
        Assert.hasText(arkProperties.getApiKey(), "ark.api-key must not be blank");
        Assert.hasText(arkProperties.getChatModel(), "ark.chat-model must not be blank");

        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .model(arkProperties.getChatModel().trim())
                .messages(List.of(
                        ChatMessage.builder()
                                .role(ChatMessageRole.SYSTEM)
                                .content(systemPrompt)
                                .build(),
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER)
                                .content(userPrompt)
                                .build()))
                .stream(false)
                .temperature(0.5D)
                .build();
        ChatCompletionResult result = getArkService().createChatCompletion(request);
        if (result == null || result.getChoices() == null || result.getChoices().isEmpty()) {
            throw new IllegalStateException("Ark chat result is empty");
        }

        ChatCompletionChoice choice = result.getChoices().getFirst();
        ChatMessage message = choice == null ? null : choice.getMessage();
        String content = message == null ? null : message.stringContent();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("Ark chat content is empty");
        }

        Usage usage = result.getUsage();
        return new ModelChatResult(
                content.trim(),
                StringUtils.hasText(result.getModel()) ? result.getModel().trim() : arkProperties.getChatModel().trim(),
                usage == null ? null : safeLongToInt(usage.getPromptTokens()),
                usage == null ? null : safeLongToInt(usage.getCompletionTokens()));
    }

    private List<Float> toFloatVector(Embedding embedding) {
        if (embedding == null || embedding.getEmbedding() == null || embedding.getEmbedding().isEmpty()) {
            throw new IllegalStateException("Ark embedding vector is empty");
        }
        return embedding.getEmbedding().stream()
                .map(Double::floatValue)
                .toList();
    }

    private List<Float> createMultiModalEmbedding(String text) {
        Assert.hasText(text, "text must not be blank");

        MultimodalEmbeddingRequest request = MultimodalEmbeddingRequest.builder()
                .model(arkProperties.getEmbeddingModel().trim())
                .input(List.of(MultimodalEmbeddingInput.builder()
                        .type(TEXT_PART_TYPE)
                        .text(text)
                        .build()))
                .build();
        MultimodalEmbeddingResult result = getArkService().createMultiModalEmbeddings(request);
        MultimodalEmbedding data = result == null ? null : result.getData();
        if (data == null || data.getEmbedding() == null || data.getEmbedding().isEmpty()) {
            throw new IllegalStateException("Ark multimodal embedding vector is empty");
        }
        return data.getEmbedding().stream()
                .map(Double::floatValue)
                .toList();
    }

    private boolean isMultiModalEmbeddingModel(String model) {
        return model.toLowerCase().contains("vision");
    }

    private Integer safeLongToInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private ArkService getArkService() {
        ArkService current = arkService;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (arkService == null) {
                arkService = ArkService.builder()
                        .apiKey(arkProperties.getApiKey())
                        .baseUrl("https://ark.cn-beijing.volces.com/api/v3")
                        .build();
            }
            return arkService;
        }
    }

    @PreDestroy
    public void shutdown() {
        if (arkService != null) {
            arkService.shutdownExecutor();
        }
    }
}
