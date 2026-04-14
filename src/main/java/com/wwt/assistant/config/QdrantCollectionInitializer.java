package com.wwt.assistant.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;
import io.qdrant.client.grpc.Points.CreateFieldIndexCollection;
import io.qdrant.client.grpc.Points.FieldType;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantCollectionInitializer {

    private static final Duration PAYLOAD_INDEX_TIMEOUT = Duration.ofSeconds(10);

    private final QdrantClient qdrantClient;
    private final QdrantProperties qdrantProperties;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeCollection() {
        try {
            String collectionName = qdrantProperties.getCollectionName();
            boolean exists = qdrantClient.collectionExistsAsync(collectionName).get();
            if (!exists) {
                VectorParams vectorParams = VectorParams.newBuilder()
                        .setSize(qdrantProperties.getVectorSize())
                        .setDistance(Distance.Cosine)
                        .build();
                qdrantClient.createCollectionAsync(collectionName, vectorParams).get();
                log.info("Created Qdrant collection: {}", collectionName);
            }

            initializePayloadIndexes(collectionName);
        } catch (Exception ex) {
            log.error("Failed to initialize Qdrant collection, collection={}", qdrantProperties.getCollectionName(), ex);
        }
    }

    private void initializePayloadIndexes(String collectionName) {
        createPayloadIndex(collectionName, "team_id", FieldType.FieldTypeInteger);
        createPayloadIndex(collectionName, "knowledge_base_id", FieldType.FieldTypeInteger);
        createPayloadIndex(collectionName, "document_id", FieldType.FieldTypeInteger);
        createPayloadIndex(collectionName, "chunk_id", FieldType.FieldTypeInteger);
    }

    private void createPayloadIndex(String collectionName, String fieldName, FieldType fieldType) {
        try {
            CreateFieldIndexCollection request = CreateFieldIndexCollection.newBuilder()
                    .setCollectionName(collectionName)
                    .setFieldName(fieldName)
                    .setFieldType(fieldType)
                    .setWait(true)
                    .build();
            qdrantClient.createPayloadIndexAsync(request, PAYLOAD_INDEX_TIMEOUT).get();
            log.info("Ensured Qdrant payload index, collection={}, field={}", collectionName, fieldName);
        } catch (Exception ex) {
            // Qdrant returns an error if the same index already exists. We keep startup tolerant here.
            log.debug("Qdrant payload index may already exist, collection={}, field={}", collectionName, fieldName, ex);
        }
    }
}
