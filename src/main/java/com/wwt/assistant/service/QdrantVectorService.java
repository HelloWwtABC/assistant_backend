package com.wwt.assistant.service;

import com.wwt.assistant.dto.vector.ChunkSearchHit;
import com.wwt.assistant.dto.vector.ChunkVectorUpsertRequest;
import java.util.List;

public interface QdrantVectorService {
    void upsertChunks(List<ChunkVectorUpsertRequest> requests);

    void deleteChunksByDocument(Long teamId, Long documentId);

    List<ChunkSearchHit> searchTeamChunks(Long teamId, List<Float> vector, int topK);
}
