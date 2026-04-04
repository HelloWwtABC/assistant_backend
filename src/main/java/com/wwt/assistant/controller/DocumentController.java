package com.wwt.assistant.controller;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.dto.document.request.BatchDeleteDocumentsRequest;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.request.UpdateDocumentRequest;
import com.wwt.assistant.dto.document.request.UploadDocumentRequest;
import com.wwt.assistant.dto.document.response.DocumentDetailResponse;
import com.wwt.assistant.dto.document.response.DocumentPageResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    @GetMapping
    public ApiResponse<DocumentPageResponse> getDocuments(@ModelAttribute DocumentQuery query) {
        return unsupported("list documents");
    }

    @GetMapping("/{documentId}")
    public ApiResponse<DocumentDetailResponse> getDocument(@PathVariable String documentId) {
        return unsupported("get document detail");
    }

    @PostMapping({"", "/upload"})
    public ApiResponse<DocumentDetailResponse> createDocument(@ModelAttribute UploadDocumentRequest request) {
        return unsupported("create document");
    }

    @PutMapping("/{documentId}")
    public ApiResponse<DocumentDetailResponse> updateDocument(
            @PathVariable String documentId,
            @RequestBody UpdateDocumentRequest request) {
        return unsupported("update document");
    }

    @DeleteMapping("/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable String documentId) {
        return unsupported("delete document");
    }

    @RequestMapping(value = {"/batch", "/batch-delete"}, method = {RequestMethod.DELETE, RequestMethod.POST})
    public ApiResponse<Void> batchDeleteDocuments(@RequestBody BatchDeleteDocumentsRequest request) {
        return unsupported("batch delete documents");
    }

    private <T> T unsupported(String operation) {
        throw new UnsupportedOperationException("TODO: implement controller endpoint: " + operation);
    }
}
