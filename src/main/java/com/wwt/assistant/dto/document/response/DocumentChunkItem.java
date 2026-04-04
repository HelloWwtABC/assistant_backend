package com.wwt.assistant.dto.document.response;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class DocumentChunkItem {
    private String chunkId;
    private Integer chunkIndex;
    private String summary;
    private Integer tokenCount;
    private String vectorStatus;
}
