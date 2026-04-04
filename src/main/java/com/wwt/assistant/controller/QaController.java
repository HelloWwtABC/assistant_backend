package com.wwt.assistant.controller;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.qa.request.ClearContextRequest;
import com.wwt.assistant.dto.qa.request.QaChatRequest;
import com.wwt.assistant.dto.qa.response.QaChatResponse;
import com.wwt.assistant.dto.qa.response.QaSessionDetailResponse;
import com.wwt.assistant.dto.qa.response.QaSessionItem;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    @GetMapping("/sessions")
    public ApiResponse<List<QaSessionItem>> getSessions() {
        return unsupported("list qa sessions");
    }

    @GetMapping("/sessions/{sessionId}")
    public ApiResponse<QaSessionDetailResponse> getSessionDetail(@PathVariable String sessionId) {
        return unsupported("get qa session detail");
    }

    @PostMapping("/sessions")
    public ApiResponse<QaSessionDetailResponse> createSession() {
        return unsupported("create qa session");
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        return unsupported("delete qa session");
    }

    @PostMapping("/chat")
    public ApiResponse<QaChatResponse> chat(@RequestBody QaChatRequest request) {
        return unsupported("qa chat");
    }

    @PostMapping("/clear-context")
    public ApiResponse<Void> clearContext(@RequestBody ClearContextRequest request) {
        return unsupported("clear qa context");
    }

    @PostMapping("/sessions/{sessionId}/clear-context")
    public ApiResponse<Void> clearContextBySessionId(@PathVariable String sessionId) {
        return unsupported("clear qa context by session id");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException("TODO: implement controller endpoint: " + operation);
    }
}
