package com.wwt.assistant.controller;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.session.request.BatchDeleteSessionsRequest;
import com.wwt.assistant.dto.session.request.SessionQuery;
import com.wwt.assistant.dto.session.response.SessionDetailResponse;
import com.wwt.assistant.dto.session.response.SessionPageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    @GetMapping
    public ApiResponse<SessionPageResponse> getSessions(@ModelAttribute SessionQuery query) {
        return unsupported("list sessions");
    }

    @GetMapping("/{sessionId}")
    public ApiResponse<SessionDetailResponse> getSessionDetail(@PathVariable String sessionId) {
        return unsupported("get session detail");
    }

    @DeleteMapping("/{sessionId}")
    public ApiResponse<Void> deleteSession(@PathVariable String sessionId) {
        return unsupported("delete session");
    }

    @RequestMapping(value = {"/batch", "/batch-delete"}, method = {RequestMethod.DELETE, RequestMethod.POST})
    public ApiResponse<Void> batchDeleteSessions(@RequestBody BatchDeleteSessionsRequest request) {
        return unsupported("batch delete sessions");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException("TODO: implement controller endpoint: " + operation);
    }
}
