package com.wwt.assistant.dto.vector;

import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChunkVectorUpsertRequest {

    private Long chunkId;

    private Long documentId;

    private Long knowledgeBaseId;

    private Long teamId;

    private Integer chunkIndex;

    private List<Float> vector;

    private String fileType;

    private String status;
}
