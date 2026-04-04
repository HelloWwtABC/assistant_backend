package com.wwt.assistant.dto.document.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DocumentListItem {
    private String documentId;
    private String documentName;
    private String knowledgeBaseId;
    private String knowledgeBaseName;
    private String fileType;
    private String status;
    private Integer chunkCount;
    private String uploader;
    private String uploadedAt;
    private String remark;
}
