package com.wwt.assistant.controller;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.PageResponse;
import com.wwt.assistant.dto.observability.request.RequestLogQuery;
import com.wwt.assistant.dto.observability.response.EvaluationSummaryResponse;
import com.wwt.assistant.dto.observability.response.MetricsOverviewResponse;
import com.wwt.assistant.dto.observability.response.RequestLogDetailResponse;
import com.wwt.assistant.dto.observability.response.RequestLogItem;
import com.wwt.assistant.dto.observability.response.RetrievalQualitySummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/observability")
public class ObservabilityController {

    @GetMapping("/metrics")
    public ApiResponse<MetricsOverviewResponse> getMetrics() {
        return unsupported("get observability metrics");
    }

    @GetMapping("/evaluation-summary")
    public ApiResponse<EvaluationSummaryResponse> getEvaluationSummary() {
        return unsupported("get evaluation summary");
    }

    @GetMapping({"/retrieval-quality", "/retrieval-quality-summary"})
    public ApiResponse<RetrievalQualitySummaryResponse> getRetrievalQualitySummary() {
        return unsupported("get retrieval quality summary");
    }

    @GetMapping("/request-logs")
    public ApiResponse<PageResponse<RequestLogItem>> getRequestLogs(@ModelAttribute RequestLogQuery query) {
        return unsupported("list request logs");
    }

    @GetMapping("/request-logs/{requestId}")
    public ApiResponse<RequestLogDetailResponse> getRequestLogDetail(@PathVariable String requestId) {
        return unsupported("get request log detail");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException("TODO: implement controller endpoint: " + operation);
    }
}
