package com.wwt.assistant.service;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.request.UploadDocumentRequest;
import com.wwt.assistant.dto.document.response.DocumentDetailResponse;
import com.wwt.assistant.dto.document.response.DocumentPageResponse;

public interface DocumentService {

    ApiResponse<DocumentPageResponse> getDocuments(DocumentQuery query);

    ApiResponse<DocumentDetailResponse> createDocument(UploadDocumentRequest request);
}
