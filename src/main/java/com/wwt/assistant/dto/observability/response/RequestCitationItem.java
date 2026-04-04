package com.wwt.assistant.dto.observability.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RequestCitationItem {
    private String documentId;
    private String documentName;
    private Integer chunkIndex;
    private String snippet;
}
