package com.wwt.assistant.service;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.qa.request.QaChatRequest;
import com.wwt.assistant.dto.qa.response.QaChatResponse;
import com.wwt.assistant.dto.qa.response.QaSessionDetailResponse;
import com.wwt.assistant.dto.qa.response.QaSessionItem;
import java.util.List;

public interface QaService {

    ApiResponse<List<QaSessionItem>> getSessions();

    ApiResponse<QaSessionDetailResponse> getSessionDetail(String sessionId);

    ApiResponse<QaSessionDetailResponse> createSession();

    ApiResponse<Void> deleteSession(String sessionId);

    ApiResponse<QaChatResponse> chat(QaChatRequest request);
}
