package com.wwt.assistant.service;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.document.request.BatchDeleteDocumentsRequest;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.request.UploadDocumentRequest;
import com.wwt.assistant.dto.document.response.DocumentDetailResponse;
import com.wwt.assistant.dto.document.response.DocumentPageResponse;
import com.wwt.assistant.entity.KbDocumentChunk;
import com.wwt.assistant.utils.parser.ParseResult;
import java.util.List;

public interface DocumentService {

    ApiResponse<DocumentPageResponse> getDocuments(DocumentQuery query);

    ApiResponse<DocumentDetailResponse> getDocument(String documentId);

    ApiResponse<DocumentDetailResponse> createDocument(UploadDocumentRequest request);

    ApiResponse<Void> deleteDocument(String documentId);

    ApiResponse<Void> batchDeleteDocuments(BatchDeleteDocumentsRequest request);

    void processDocumentAsync(Long teamId, Long documentId);

    void processDocument(Long teamId, Long documentId);

    int chunkAndSaveDocument(Long teamId, Long documentId, ParseResult parseResult);

    void embedAndUpsertDocumentChunks(Long teamId, List<KbDocumentChunk> chunks);

    void cleanupDeletedDocumentResourcesAsync(Long teamId, Long documentId, String fileUrl);
}
