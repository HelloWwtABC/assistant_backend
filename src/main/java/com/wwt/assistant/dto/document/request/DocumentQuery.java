package com.wwt.assistant.dto.document.request;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DocumentQuery {
    private String keyword;
    private String status;
    private String uploader;
    private Long page;
    private Long pageSize;
}
