package com.wwt.assistant.controller;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.PageResponse;
import com.wwt.assistant.dto.tool.request.CreateTicketRequest;
import com.wwt.assistant.dto.tool.request.ServiceHealthQueryRequest;
import com.wwt.assistant.dto.tool.request.TicketStatusQueryRequest;
import com.wwt.assistant.dto.tool.request.ToolCallRecordQuery;
import com.wwt.assistant.dto.tool.response.CreateTicketResponse;
import com.wwt.assistant.dto.tool.response.ServiceHealthResponse;
import com.wwt.assistant.dto.tool.response.TicketStatusResponse;
import com.wwt.assistant.dto.tool.response.ToolCallRecordItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tools")
public class ToolController {

    @PostMapping({"/ticket-status", "/ticket-status/query"})
    public ApiResponse<TicketStatusResponse> queryTicketStatus(@RequestBody TicketStatusQueryRequest request) {
        return unsupported("query ticket status");
    }

    @PostMapping({"/create-ticket", "/tickets"})
    public ApiResponse<CreateTicketResponse> createTicket(@RequestBody CreateTicketRequest request) {
        return unsupported("create ticket");
    }

    @PostMapping({"/service-health", "/service-health/query"})
    public ApiResponse<ServiceHealthResponse> queryServiceHealth(@RequestBody ServiceHealthQueryRequest request) {
        return unsupported("query service health");
    }

    @GetMapping("/call-records")
    public ApiResponse<PageResponse<ToolCallRecordItem>> getCallRecords(@ModelAttribute ToolCallRecordQuery query) {
        return unsupported("list tool call records");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException("TODO: implement controller endpoint: " + operation);
    }
}
