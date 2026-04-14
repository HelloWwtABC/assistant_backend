package com.wwt.assistant.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.model.dto.PaperMethodItem;
import com.wwt.assistant.agent.service.MethodExtractionAiService;
import com.wwt.assistant.agent.service.MethodExtractionService;
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
 * 基于 LangChain4j 的论文方法提取服务实现。
 */
@Service
public class MethodExtractionServiceImpl implements MethodExtractionService {

    private static final int MAX_CONTENT_LENGTH = 1800;
    private static final int MAX_SUMMARY_LENGTH = 400;
    private static final int MAX_SECTION_TITLE_LENGTH = 200;

    private final ObjectMapper objectMapper;
    private final MethodExtractionAiService aiService;

    public MethodExtractionServiceImpl(
            @Qualifier("planningAgentChatModel") ChatModel planningAgentChatModel,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.aiService = AiServices.create(MethodExtractionAiService.class, planningAgentChatModel);
    }

    @Override
    public MethodExtractionResult extractMethods(
            String originalQuestion,
            String rewrittenQuestion,
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords) {
        if (CollectionUtils.isEmpty(chunkRecords)) {
            throw new IllegalArgumentException("缺少方法提取所需输入 chunks");
        }

        try {
            String question = resolveQuestion(originalQuestion, rewrittenQuestion);
            String chunkPayload = buildChunkPayload(chunkRecords);
            MethodExtractionResult result = aiService.extractMethods(question, chunkPayload);
            MethodExtractionResult normalizedResult = normalizeResult(result);
            if (CollectionUtils.isEmpty(normalizedResult.getItems())) {
                throw new IllegalStateException("结构化结果为空，未提取到可用的方法信息");
            }
            return normalizedResult;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("方法提取模型调用失败", ex);
        }
    }

    private String resolveQuestion(String originalQuestion, String rewrittenQuestion) {
        if (StringUtils.hasText(rewrittenQuestion)) {
            return rewrittenQuestion.trim();
        }
        if (StringUtils.hasText(originalQuestion)) {
            return originalQuestion.trim();
        }
        return "请提取各篇论文的方法信息";
    }

    private String buildChunkPayload(List<DocumentChunkMapper.QaChunkRecord> chunkRecords) throws Exception {
        Map<String, PaperChunkGroup> groups = groupChunks(chunkRecords);
        ArrayNode groupArray = objectMapper.createArrayNode();
        for (PaperChunkGroup group : groups.values()) {
            ObjectNode groupNode = groupArray.addObject();
            if (StringUtils.hasText(group.paperTitle())) {
                groupNode.put("paperTitle", group.paperTitle());
            } else {
                groupNode.putNull("paperTitle");
            }
            if (StringUtils.hasText(group.paperId())) {
                groupNode.put("paperId", group.paperId());
            } else {
                groupNode.putNull("paperId");
            }
            groupNode.put("groupKey", group.groupKey());
            if (StringUtils.hasText(group.sourceName())) {
                groupNode.put("sourceName", group.sourceName());
            } else {
                groupNode.putNull("sourceName");
            }

            ArrayNode chunksArray = groupNode.putArray("chunks");
            for (DocumentChunkMapper.QaChunkRecord chunkRecord : group.chunkRecords()) {
                ObjectNode chunkNode = chunksArray.addObject();
                if (chunkRecord.getChunkId() != null) {
                    chunkNode.put("chunkId", chunkRecord.getChunkId());
                } else {
                    chunkNode.putNull("chunkId");
                }
                if (chunkRecord.getChunkIndex() != null) {
                    chunkNode.put("chunkIndex", chunkRecord.getChunkIndex());
                } else {
                    chunkNode.putNull("chunkIndex");
                }
                if (chunkRecord.getPageNo() != null) {
                    chunkNode.put("pageNo", chunkRecord.getPageNo());
                } else {
                    chunkNode.putNull("pageNo");
                }
                putNullableText(chunkNode, "sectionTitle", trimToLength(chunkRecord.getSectionTitle(), MAX_SECTION_TITLE_LENGTH));
                putNullableText(chunkNode, "summary", trimToLength(chunkRecord.getSummary(), MAX_SUMMARY_LENGTH));
                putNullableText(chunkNode, "content", trimToLength(chunkRecord.getContent(), MAX_CONTENT_LENGTH));
            }
        }
        return objectMapper.writeValueAsString(groupArray);
    }

    private Map<String, PaperChunkGroup> groupChunks(List<DocumentChunkMapper.QaChunkRecord> chunkRecords) {
        Map<String, PaperChunkGroup> groups = new LinkedHashMap<>();
        AtomicInteger unknownCounter = new AtomicInteger(1);
        for (DocumentChunkMapper.QaChunkRecord chunkRecord : chunkRecords) {
            if (chunkRecord == null) {
                continue;
            }
            String paperTitle = normalizeText(chunkRecord.getDocumentName());
            String paperId = chunkRecord.getDocumentId() == null ? null : String.valueOf(chunkRecord.getDocumentId());
            String sourceName = normalizeText(chunkRecord.getDocumentName());
            String groupKey = resolveGroupKey(paperTitle, paperId, sourceName, unknownCounter);

            PaperChunkGroup group = groups.computeIfAbsent(
                    groupKey,
                    key -> new PaperChunkGroup(paperTitle, paperId, key, sourceName, new ArrayList<>()));
            group.chunkRecords().add(chunkRecord);
        }
        return groups;
    }

    private String resolveGroupKey(
            String paperTitle,
            String paperId,
            String sourceName,
            AtomicInteger unknownCounter) {
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

    private MethodExtractionResult normalizeResult(MethodExtractionResult result) {
        if (result == null) {
            throw new IllegalStateException("结构化结果解析失败");
        }

        List<PaperMethodItem> normalizedItems = new ArrayList<>();
        if (!CollectionUtils.isEmpty(result.getItems())) {
            for (PaperMethodItem item : result.getItems()) {
                PaperMethodItem normalizedItem = normalizeItem(item);
                if (isUsable(normalizedItem)) {
                    normalizedItems.add(normalizedItem);
                }
            }
        }

        MethodExtractionResult normalizedResult = new MethodExtractionResult();
        normalizedResult.setItems(normalizedItems);
        normalizedResult.setOverallSummary(StringUtils.hasText(result.getOverallSummary())
                ? result.getOverallSummary().trim()
                : "已提取论文方法信息");
        return normalizedResult;
    }

    private PaperMethodItem normalizeItem(PaperMethodItem item) {
        if (item == null) {
            return null;
        }
        PaperMethodItem normalizedItem = new PaperMethodItem();
        normalizedItem.setPaperTitle(normalizeText(item.getPaperTitle()));
        normalizedItem.setPaperId(normalizeText(item.getPaperId()));
        normalizedItem.setMethodSummary(normalizeText(item.getMethodSummary()));
        normalizedItem.setMethodKeywords(normalizeStringList(item.getMethodKeywords()));
        normalizedItem.setRawEvidenceSnippets(normalizeStringList(item.getRawEvidenceSnippets()));
        return normalizedItem;
    }

    private boolean isUsable(PaperMethodItem item) {
        if (item == null) {
            return false;
        }
        return StringUtils.hasText(item.getMethodSummary())
                || !CollectionUtils.isEmpty(item.getMethodKeywords())
                || !CollectionUtils.isEmpty(item.getRawEvidenceSnippets());
    }

    private List<String> normalizeStringList(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            String normalizedValue = normalizeText(value);
            if (StringUtils.hasText(normalizedValue)) {
                normalizedValues.add(normalizedValue);
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
