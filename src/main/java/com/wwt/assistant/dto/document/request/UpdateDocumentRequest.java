package com.wwt.assistant.dto.document.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateDocumentRequest {
    private String knowledgeBaseId;
    private String documentName;
    private String status;
    private String remark;
}
