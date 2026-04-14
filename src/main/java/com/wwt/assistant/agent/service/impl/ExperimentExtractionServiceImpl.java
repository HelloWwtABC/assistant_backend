package com.wwt.assistant.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.PaperExperimentItem;
import com.wwt.assistant.agent.service.ExperimentExtractionAiService;
import com.wwt.assistant.agent.service.ExperimentExtractionService;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 基于 LangChain4j 的实验信息提取服务实现。
 */
@Service
public class ExperimentExtractionServiceImpl implements ExperimentExtractionService {

    private static final int MAX_CONTENT_LENGTH = 1800;
    private static final int MAX_SUMMARY_LENGTH = 400;
    private static final int MAX_SECTION_TITLE_LENGTH = 200;

    private final ObjectMapper objectMapper;
    private final ExperimentExtractionAiService aiService;

    public ExperimentExtractionServiceImpl(
            @Qualifier("planningAgentChatModel") ChatModel planningAgentChatModel,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.aiService = AiServices.create(ExperimentExtractionAiService.class, planningAgentChatModel);
    }

    @Override
    public ExperimentExtractionResult extractExperiments(
            String originalQuestion,
            String rewrittenQuestion,
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords) {
        if (CollectionUtils.isEmpty(chunkRecords)) {
            throw new IllegalArgumentException("缺少实验提取所需输入 chunks");
        }

        try {
            ExperimentExtractionResult result = aiService.extractExperiments(
                    resolveQuestion(originalQuestion, rewrittenQuestion),
                    buildChunkPayload(chunkRecords));
            ExperimentExtractionResult normalized = normalizeResult(result);
            if (CollectionUtils.isEmpty(normalized.getItems())) {
                throw new IllegalStateException("结构化结果为空，未提取到可用的实验信息");
            }
            return normalized;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("实验提取模型调用失败", ex);
        }
    }

    private String resolveQuestion(String originalQuestion, String rewrittenQuestion) {
        if (StringUtils.hasText(rewrittenQuestion)) {
            return rewrittenQuestion.trim();
        }
        if (StringUtils.hasText(originalQuestion)) {
            return originalQuestion.trim();
        }
        return "请提取各篇论文的实验设置与实验结果";
    }

    private String buildChunkPayload(List<DocumentChunkMapper.QaChunkRecord> chunkRecords) throws Exception {
        ArrayNode groupsArray = objectMapper.createArrayNode();
        for (PaperChunkGroup group : groupChunks(chunkRecords).values()) {
            ObjectNode groupNode = groupsArray.addObject();
            putNullableText(groupNode, "paperTitle", group.paperTitle());
            putNullableText(groupNode, "paperId", group.paperId());
            putNullableText(groupNode, "groupKey", group.groupKey());
            putNullableText(groupNode, "sourceName", group.sourceName());

            ArrayNode chunksArray = groupNode.putArray("chunks");
            for (DocumentChunkMapper.QaChunkRecord chunk : group.chunkRecords()) {
                ObjectNode chunkNode = chunksArray.addObject();
                if (chunk.getChunkId() != null) {
                    chunkNode.put("chunkId", chunk.getChunkId());
                } else {
                    chunkNode.putNull("chunkId");
                }
                if (chunk.getChunkIndex() != null) {
                    chunkNode.put("chunkIndex", chunk.getChunkIndex());
                } else {
                    chunkNode.putNull("chunkIndex");
                }
                if (chunk.getPageNo() != null) {
                    chunkNode.put("pageNo", chunk.getPageNo());
                } else {
                    chunkNode.putNull("pageNo");
                }
                putNullableText(chunkNode, "sectionTitle", trimToLength(chunk.getSectionTitle(), MAX_SECTION_TITLE_LENGTH));
                putNullableText(chunkNode, "summary", trimToLength(chunk.getSummary(), MAX_SUMMARY_LENGTH));
                putNullableText(chunkNode, "content", trimToLength(chunk.getContent(), MAX_CONTENT_LENGTH));
            }
        }
        return objectMapper.writeValueAsString(groupsArray);
    }

    /**
     * 把一堆零散的 chunk（论文片段），按「同一篇文章」分组打包，
     * @param chunkRecords
     * @return
     */
    private Map<String, PaperChunkGroup> groupChunks(List<DocumentChunkMapper.QaChunkRecord> chunkRecords) {
        Map<String, PaperChunkGroup> groups = new LinkedHashMap<>();
        AtomicInteger unknownCounter = new AtomicInteger(1);
        for (DocumentChunkMapper.QaChunkRecord chunk : chunkRecords) {
            if (chunk == null) {
                continue;
            }
            String paperTitle = normalizeText(chunk.getDocumentName());
            String paperId = chunk.getDocumentId() == null ? null : String.valueOf(chunk.getDocumentId());
            String sourceName = normalizeText(chunk.getDocumentName());
            String groupKey = resolveGroupKey(paperTitle, paperId, sourceName, unknownCounter);
            groups.computeIfAbsent(
                            groupKey,
                            key -> new PaperChunkGroup(paperTitle, paperId, key, sourceName, new ArrayList<>()))
                    .chunkRecords()
                    .add(chunk);
        }
        return groups;
    }

    private String resolveGroupKey(String paperTitle, String paperId, String sourceName, AtomicInteger unknownCounter) {
        if (StringUtils.hasText(paperTitle)) {
            return paperTitle;
        }
        if (StringUtils.hasText(paperId)) {
            return "paper-" + paperId;
        }
        if (StringUtils.hasText(sourceName)) {
            return sourceName;
        }
        return "Unknown Paper " + unknownCounter.getAndIncrement();
    }

    private ExperimentExtractionResult normalizeResult(ExperimentExtractionResult result) {
        if (result == null) {
            throw new IllegalStateException("结构化结果解析失败");
        }

        List<PaperExperimentItem> items = new ArrayList<>();
        if (!CollectionUtils.isEmpty(result.getItems())) {
            for (PaperExperimentItem item : result.getItems()) {
                PaperExperimentItem normalized = normalizeItem(item);
                if (isUsable(normalized)) {
                    items.add(normalized);
                }
            }
        }

        ExperimentExtractionResult normalizedResult = new ExperimentExtractionResult();
        normalizedResult.setItems(items);
        normalizedResult.setOverallSummary(StringUtils.hasText(result.getOverallSummary())
                ? result.getOverallSummary().trim()
                : "已提取论文实验信息");
        return normalizedResult;
    }

    private PaperExperimentItem normalizeItem(PaperExperimentItem item) {
        if (item == null) {
            return null;
        }
        PaperExperimentItem normalized = new PaperExperimentItem();
        normalized.setPaperTitle(normalizeText(item.getPaperTitle()));
        normalized.setPaperId(normalizeText(item.getPaperId()));
        normalized.setExperimentSetupSummary(normalizeText(item.getExperimentSetupSummary()));
        normalized.setDatasetNames(normalizeStringList(item.getDatasetNames()));
        normalized.setMetricNames(normalizeStringList(item.getMetricNames()));
        normalized.setBaselineModels(normalizeStringList(item.getBaselineModels()));
        normalized.setResultSummary(normalizeText(item.getResultSummary()));
        normalized.setKeyFindings(normalizeStringList(item.getKeyFindings()));
        normalized.setRawEvidenceSnippets(normalizeStringList(item.getRawEvidenceSnippets()));
        return normalized;
    }

    private boolean isUsable(PaperExperimentItem item) {
        return item != null
                && (StringUtils.hasText(item.getExperimentSetupSummary())
                        || StringUtils.hasText(item.getResultSummary())
                        || !CollectionUtils.isEmpty(item.getDatasetNames())
                        || !CollectionUtils.isEmpty(item.getMetricNames())
                        || !CollectionUtils.isEmpty(item.getBaselineModels())
                        || !CollectionUtils.isEmpty(item.getKeyFindings())
                        || !CollectionUtils.isEmpty(item.getRawEvidenceSnippets()));
    }

    private List<String> normalizeStringList(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalized = normalizeText(value);
            if (StringUtils.hasText(normalized)) {
                normalizedValues.add(normalized);
            }
        }
        return List.copyOf(normalizedValues);
    }

    private String trimToLength(String value, int maxLength) {
        String normalized = normalizeText(value);
        if (!StringUtils.hasText(normalized) || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void putNullableText(ObjectNode node, String fieldName, String value) {
        if (StringUtils.hasText(value)) {
            node.put(fieldName, value);
        } else {
            node.putNull(fieldName);
        }
    }

    private record PaperChunkGroup(
            String paperTitle,
            String paperId,
            String groupKey,
            String sourceName,
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords) {
    }
}
