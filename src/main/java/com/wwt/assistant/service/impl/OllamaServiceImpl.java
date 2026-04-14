package com.wwt.assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wwt.assistant.config.OllamaProperties;
import com.wwt.assistant.dto.model.ModelChatResult;
import com.wwt.assistant.service.OllamaService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OllamaServiceImpl implements OllamaService {

    private final OllamaProperties ollamaProperties;
    private final ObjectMapper objectMapper;

    public OllamaServiceImpl(OllamaProperties ollamaProperties, ObjectMapper objectMapper) {
        this.ollamaProperties = ollamaProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public ModelChatResult chat(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(systemPrompt)) {
            throw new IllegalArgumentException("systemPrompt must not be blank");
        }
        if (!StringUtils.hasText(userPrompt)) {
            throw new IllegalArgumentException("userPrompt must not be blank");
        }

        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", ollamaProperties.getModel().trim());
            requestBody.put("stream", Boolean.TRUE.equals(ollamaProperties.getStream()));

            ArrayNode messages = requestBody.putArray("messages");
            messages.addObject()
                    .put("role", "system")
                    .put("content", systemPrompt.trim());
            messages.addObject()
                    .put("role", "user")
                    .put("content", userPrompt.trim());

            ObjectNode options = requestBody.putObject("options");
            if (ollamaProperties.getTemperature() != null) {
                options.put("temperature", ollamaProperties.getTemperature());
            }
            if (ollamaProperties.getNumCtx() != null && ollamaProperties.getNumCtx() > 0) {
                options.put("num_ctx", ollamaProperties.getNumCtx());
            }

            HttpResponse<String> response = sendJsonRequest("/api/chat", requestBody);
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode messageNode = root == null ? null : root.get("message");
            String content = messageNode == null ? null : textValue(messageNode.get("content"));
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("Ollama chat content is empty");
            }

            String modelName = textValue(root == null ? null : root.get("model"));
            Integer promptTokens = intValue(root == null ? null : root.get("prompt_eval_count"));
            Integer completionTokens = intValue(root == null ? null : root.get("eval_count"));
            return new ModelChatResult(
                    content.trim(),
                    StringUtils.hasText(modelName) ? modelName : ollamaProperties.getModel().trim(),
                    promptTokens,
                    completionTokens);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to call Ollama chat api", ex);
        }
    }

    @Override
    public boolean modelExists() {
        validateModelConfigured();
        try {
            HttpRequest request = HttpRequest.newBuilder(buildUri("/api/tags"))
                    .GET()
                    .timeout(resolveTimeout())
                    .build();
            HttpResponse<String> response = newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            ensureSuccessStatus(response.statusCode(), "Ollama tags request");

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode models = root == null ? null : root.get("models");
            if (models == null || !models.isArray()) {
                return false;
            }

            String targetModel = ollamaProperties.getModel().trim();
            for (JsonNode modelNode : models) {
                if (modelNode == null || modelNode.isNull()) {
                    continue;
                }
                String name = textValue(modelNode.get("name"));
                if (targetModel.equalsIgnoreCase(name)) {
                    return true;
                }
                String model = textValue(modelNode.get("model"));
                if (targetModel.equalsIgnoreCase(model)) {
                    return true;
                }
            }
            return false;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to query Ollama model list", ex);
        }
    }

    @Override
    public void pullModel() {
        validateModelConfigured();
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", ollamaProperties.getModel().trim());
            requestBody.put("stream", false);
            sendJsonRequest("/api/pull", requestBody);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to pull Ollama model", ex);
        }
    }

    private HttpResponse<String> sendJsonRequest(String path, JsonNode body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(buildUri(path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .timeout(resolveTimeout())
                .build();
        HttpResponse<String> response = newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        ensureSuccessStatus(response.statusCode(), "Ollama request");
        return response;
    }

    private void ensureSuccessStatus(int statusCode, String action) {
        if (statusCode < 200 || statusCode >= 300) {
            throw new IllegalStateException(action + " failed with status " + statusCode);
        }
    }

    private void validateModelConfigured() {
        if (!StringUtils.hasText(ollamaProperties.getModel())) {
            throw new IllegalStateException("ollama.model must not be blank");
        }
    }

    private HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(resolveTimeout())
                .build();
    }

    private Duration resolveTimeout() {
        Integer timeoutSeconds = ollamaProperties.getTimeoutSeconds();
        int seconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 120 : timeoutSeconds;
        return Duration.ofSeconds(seconds);
    }

    private URI buildUri(String path) {
        String baseUrl = StringUtils.hasText(ollamaProperties.getBaseUrl())
                ? ollamaProperties.getBaseUrl().trim()
                : "http://localhost:11434";
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBaseUrl + path);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String text = node.asText();
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private Integer intValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isInt() || node.isLong()) {
            return node.intValue();
        }
        String text = node.asText();
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
