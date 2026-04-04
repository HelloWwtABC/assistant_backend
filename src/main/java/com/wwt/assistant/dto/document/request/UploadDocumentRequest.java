package com.wwt.assistant.dto.document.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

@Data
@NoArgsConstructor
public class UploadDocumentRequest {
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private MultipartFile file;
    private String remark;
    private String uploader;
}
