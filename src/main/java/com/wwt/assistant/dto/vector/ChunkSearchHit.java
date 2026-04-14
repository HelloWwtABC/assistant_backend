package com.wwt.assistant.dto.vector;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkSearchHit {
    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private Float score;
}
