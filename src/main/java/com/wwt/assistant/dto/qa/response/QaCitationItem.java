package com.wwt.assistant.dto.qa.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QaCitationItem {
    private String citationId;
    private String documentId;
    private String documentName;
    private Integer chunkIndex;
    private String snippet;
}
