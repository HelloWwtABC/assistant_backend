package com.wwt.assistant.service.impl;

import com.wwt.assistant.config.QdrantProperties;
import com.wwt.assistant.dto.vector.ChunkSearchHit;
import com.wwt.assistant.dto.vector.ChunkVectorUpsertRequest;
import com.wwt.assistant.service.QdrantVectorService;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import static io.qdrant.client.ConditionFactory.match;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorsFactory.vectors;

@Service
public class QdrantVectorServiceImpl implements QdrantVectorService {

    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;

    public QdrantVectorServiceImpl(QdrantClient qdrantClient, QdrantProperties qdrantProperties) {
        this.qdrantClient = qdrantClient;
        this.qdrantProperties = qdrantProperties;
    }

    @Override
    public void upsertChunks(List<ChunkVectorUpsertRequest> requests) {
        Assert.notEmpty(requests, "requests must not be empty");
        List<Points.PointStruct> points = requests.stream()
                .map(this::toPoint)
                .toList();
        try {
            qdrantClient.upsertAsync(qdrantProperties.getCollectionName(), points).get();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upsert chunk vectors to Qdrant", ex);
        }
    }

    @Override
    public void deleteChunksByDocument(Long teamId, Long documentId) {
        Assert.notNull(teamId, "teamId must not be null");
        Assert.notNull(documentId, "documentId must not be null");

        Common.Filter filter = Common.Filter.newBuilder()
                .addMust(match("team_id", teamId))
                .addMust(match("document_id", documentId))
                .build();
        try {
            qdrantClient.deleteAsync(qdrantProperties.getCollectionName(), filter).get();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to delete chunk vectors from Qdrant", ex);
        }
    }

    @Override
    public List<ChunkSearchHit> searchTeamChunks(Long teamId, List<Float> vector, int topK) {
        Assert.notNull(teamId, "teamId must not be null");
        Assert.notEmpty(vector, "vector must not be empty");
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0");
        }
        if (vector.size() != qdrantProperties.getVectorSize()) {
            throw new IllegalArgumentException("vector size does not match qdrant.vector-size");
        }

        Points.SearchPoints searchPoints = Points.SearchPoints.newBuilder()
                .setCollectionName(qdrantProperties.getCollectionName())
                .addAllVector(vector)
                .setFilter(Common.Filter.newBuilder()
                        .addMust(match("team_id", teamId))
                        .build())
                .setLimit(topK)
                .setWithPayload(io.qdrant.client.WithPayloadSelectorFactory.enable(true))
                .setWithVectors(io.qdrant.client.WithVectorsSelectorFactory.enable(false))
                .build();
        try {
            List<Points.ScoredPoint> points = qdrantClient.searchAsync(searchPoints).get();
            List<ChunkSearchHit> hits = new ArrayList<>(points.size());
            for (Points.ScoredPoint point : points) {
                ChunkSearchHit hit = toChunkSearchHit(point);
                if (hit != null) {
                    hits.add(hit);
                }
            }
            return hits;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to search chunk vectors from Qdrant", ex);
        }
    }

    private Points.PointStruct toPoint(ChunkVectorUpsertRequest request) {
        validateRequest(request);
        return Points.PointStruct.newBuilder()
                .setId(id(request.getChunkId()))
                .setVectors(vectors(request.getVector()))
                .putAllPayload(buildPayload(request))
                .build();
    }

    private void validateRequest(ChunkVectorUpsertRequest request) {
        Assert.notNull(request, "request must not be null");
        Assert.notNull(request.getChunkId(), "chunkId must not be null");
        Assert.notNull(request.getDocumentId(), "documentId must not be null");
        Assert.notNull(request.getKnowledgeBaseId(), "knowledgeBaseId must not be null");
        Assert.notNull(request.getTeamId(), "teamId must not be null");
        Assert.notEmpty(request.getVector(), "vector must not be empty");
        if (request.getVector().size() != qdrantProperties.getVectorSize()) {
            throw new IllegalArgumentException("vector size does not match qdrant.vector-size");
        }
    }

    private Map<String, JsonWithInt.Value> buildPayload(ChunkVectorUpsertRequest request) {
        Map<String, JsonWithInt.Value> payload = new LinkedHashMap<>();
        payload.put("team_id", value(request.getTeamId()));
        payload.put("knowledge_base_id", value(request.getKnowledgeBaseId()));
        payload.put("document_id", value(request.getDocumentId()));
        payload.put("chunk_id", value(request.getChunkId()));
        if (request.getChunkIndex() != null) {
            payload.put("chunk_index", value(request.getChunkIndex().longValue()));
        }
        if (StringUtils.hasText(request.getFileType())) {
            payload.put("file_type", value(request.getFileType()));
        }
        if (StringUtils.hasText(request.getStatus())) {
            payload.put("status", value(request.getStatus()));
        }
        return payload;
    }

    private ChunkSearchHit toChunkSearchHit(Points.ScoredPoint point) {
        if (point == null || point.getPayloadMap().isEmpty()) {
            return null;
        }
        Map<String, JsonWithInt.Value> payloadMap = point.getPayloadMap();
        Long chunkId = readLong(payloadMap.get("chunk_id"));
        if (chunkId == null) {
            return null;
        }
        return new ChunkSearchHit(
                chunkId,
                readLong(payloadMap.get("document_id")),
                readInteger(payloadMap.get("chunk_index")),
                point.getScore());
    }

    private Long readLong(JsonWithInt.Value value) {
        if (value == null) {
            return null;
        }
        return switch (value.getKindCase()) {
            case INTEGER_VALUE -> value.getIntegerValue();
            case DOUBLE_VALUE -> (long) value.getDoubleValue();
            case STRING_VALUE -> {
                String text = value.getStringValue();
                if (!StringUtils.hasText(text)) {
                    yield null;
                }
                try {
                    yield Long.parseLong(text.trim());
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private Integer readInteger(JsonWithInt.Value value) {
        Long longValue = readLong(value);
        return longValue == null ? null : longValue.intValue();
    }
}
