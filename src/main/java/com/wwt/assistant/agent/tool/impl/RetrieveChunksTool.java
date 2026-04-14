package com.wwt.assistant.agent.tool.impl;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.tool.AgentTool;
import com.wwt.assistant.common.UserContextHolder;
import com.wwt.assistant.dto.vector.ChunkSearchHit;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import com.wwt.assistant.service.ArkModelService;
import com.wwt.assistant.service.QdrantVectorService;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 执行知识分片检索的工具实现。
 */
@Component
public class RetrieveChunksTool implements AgentTool {

    private static final String TOOL_NAME = "retrieveChunks";
    private static final String STEP_STATUS_COMPLETED = "completed";
    private static final String STEP_STATUS_FAILED = "failed";
    private static final int DEFAULT_TOP_K = 5;

    private final ArkModelService arkModelService;
    private final QdrantVectorService qdrantVectorService;
    private final DocumentChunkMapper documentChunkMapper;

    public RetrieveChunksTool(
            ArkModelService arkModelService,
            QdrantVectorService qdrantVectorService,
            DocumentChunkMapper documentChunkMapper) {
        this.arkModelService = arkModelService;
        this.qdrantVectorService = qdrantVectorService;
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public String getToolName() {
        return TOOL_NAME;
    }

    @Override
    public AgentStepResult execute(AgentExecutionContext context, AgentStep step) {
        long startNanos = System.nanoTime();
        AgentStepResult result = buildBaseResult(step);

        try {
            Long teamId = UserContextHolder.getTeamId();
            if (teamId == null) {
                return fail(result, "Current team context is missing", startNanos);
            }

            String query = resolveQuery(context);
            if (!StringUtils.hasText(query)) {
                return fail(result, "No query available for chunk retrieval", startNanos);
            }

            List<Float> queryVector = arkModelService.createEmbeddings(List.of(query)).getFirst();
            List<ChunkSearchHit> searchHits = qdrantVectorService.searchTeamChunks(teamId, queryVector, DEFAULT_TOP_K);
            // 查询命中chunk的详细信息
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords = loadChunkRecords(teamId, searchHits, context);

            context.setRetrievedChunks(new ArrayList<>(chunkRecords));
            // 保存中间结果
            if (StringUtils.hasText(step == null ? null : step.getOutputKey())) {
                ensureIntermediateResults(context).put(step.getOutputKey(), new ArrayList<>(chunkRecords));
            }

            result.setSuccess(Boolean.TRUE);
            result.setStatus(STEP_STATUS_COMPLETED);
            result.setOutput(chunkRecords);
            result.setSummary("Retrieved " + chunkRecords.size() + " chunks");
            result.setLatencyMs(calculateLatencyMs(startNanos));
            result.setRetryCount(0);
            return result;
        } catch (Exception ex) {
            return fail(result, ex.getMessage(), startNanos);
        }
    }

    private List<DocumentChunkMapper.QaChunkRecord> loadChunkRecords(
            Long teamId,
            List<ChunkSearchHit> searchHits,
            AgentExecutionContext context) {
        if (searchHits == null || searchHits.isEmpty()) {
            return List.of();
        }
        // 去除无效，重复chunkid
        List<Long> chunkIds = searchHits.stream()
                .map(ChunkSearchHit::getChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return List.of();
        }

        Map<Long, Integer> chunkOrderMap = new LinkedHashMap<>(chunkIds.size());
        for (int i = 0; i < chunkIds.size(); i++) {
            chunkOrderMap.put(chunkIds.get(i), i);
        }

        List<DocumentChunkMapper.QaChunkRecord> records = documentChunkMapper.selectQaChunkRecords(teamId, chunkIds);
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        Set<Long> allowedKnowledgeBaseIds = normalizeKnowledgeBaseIds(context == null ? null : context.getKnowledgeBaseIds());
        return records.stream()
                .filter(record -> matchesKnowledgeBase(record, allowedKnowledgeBaseIds))
                .sorted(Comparator.comparingInt(left -> chunkOrderMap.getOrDefault(left.getChunkId(), Integer.MAX_VALUE)))
                .toList();
    }

    private boolean matchesKnowledgeBase(DocumentChunkMapper.QaChunkRecord record, Set<Long> knowledgeBaseIds) {
        if (record == null) {
            return false;
        }
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return true;
        }
        return record.getKnowledgeBaseId() != null && knowledgeBaseIds.contains(record.getKnowledgeBaseId());
    }

    private Set<Long> normalizeKnowledgeBaseIds(List<Long> knowledgeBaseIds) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return Set.of();
        }
        return knowledgeBaseIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String resolveQuery(AgentExecutionContext context) {
        if (context == null) {
            return null;
        }
        if (StringUtils.hasText(context.getRewrittenQuestion())) {
            return context.getRewrittenQuestion().trim();
        }
        if (StringUtils.hasText(context.getOriginalQuestion())) {
            return context.getOriginalQuestion().trim();
        }
        return null;
    }

    private Map<String, Object> ensureIntermediateResults(AgentExecutionContext context) {
        Map<String, Object> intermediateResults = context.getIntermediateResults();
        if (intermediateResults == null) {
            intermediateResults = new LinkedHashMap<>();
            context.setIntermediateResults(intermediateResults);
        }
        return intermediateResults;
    }

    private AgentStepResult buildBaseResult(AgentStep step) {
        AgentStepResult result = new AgentStepResult();
        if (step != null) {
            result.setStepNo(step.getStepNo());
            result.setAction(step.getAction());
        }
        result.setRetryCount(0);
        return result;
    }

    private AgentStepResult fail(AgentStepResult result, String errorMessage, long startNanos) {
        result.setSuccess(Boolean.FALSE);
        result.setStatus(STEP_STATUS_FAILED);
        result.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage : "Retrieve chunks failed");
        result.setSummary("Chunk retrieval failed");
        result.setLatencyMs(calculateLatencyMs(startNanos));
        return result;
    }

    private long calculateLatencyMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
