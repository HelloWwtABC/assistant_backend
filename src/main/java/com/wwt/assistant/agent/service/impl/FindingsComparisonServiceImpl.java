package com.wwt.assistant.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.model.dto.PaperComparisonItem;
import com.wwt.assistant.agent.service.FindingsComparisonAiService;
import com.wwt.assistant.agent.service.FindingsComparisonService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 基于 LangChain4j 的发现对比服务实现。
 */
@Service
public class FindingsComparisonServiceImpl implements FindingsComparisonService {

    private final ObjectMapper objectMapper;
    private final FindingsComparisonAiService aiService;

    public FindingsComparisonServiceImpl(
            @Qualifier("planningAgentChatModel") ChatModel planningAgentChatModel,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.aiService = AiServices.create(FindingsComparisonAiService.class, planningAgentChatModel);
    }

    @Override
    public FindingsComparisonResult compareFindings(
            String originalQuestion,
            String rewrittenQuestion,
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult) {
        if (methodResult == null && experimentResult == null) {
            throw new IllegalArgumentException("缺少对比所需的 methods 和 experimentResults");
        }

        try {
            FindingsComparisonResult result = aiService.compareFindings(
                    resolveQuestion(originalQuestion, rewrittenQuestion),
                    buildComparisonPayload(methodResult, experimentResult));
            FindingsComparisonResult normalized = normalizeResult(result);
            if (!hasUsableContent(normalized)) {
                throw new IllegalStateException("结构化结果为空，未生成可用的对比信息");
            }
            return normalized;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("对比分析模型调用失败", ex);
        }
    }

    private String resolveQuestion(String originalQuestion, String rewrittenQuestion) {
        if (StringUtils.hasText(rewrittenQuestion)) {
            return rewrittenQuestion.trim();
        }
        if (StringUtils.hasText(originalQuestion)) {
            return originalQuestion.trim();
        }
        return "请对比这些论文的方法和实验结果";
    }

    private String buildComparisonPayload(
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        if (methodResult != null) {
            root.set("methods", objectMapper.valueToTree(methodResult));
        } else {
            root.putNull("methods");
        }
        if (experimentResult != null) {
            root.set("experimentResults", objectMapper.valueToTree(experimentResult));
        } else {
            root.putNull("experimentResults");
        }
        return objectMapper.writeValueAsString(root);
    }

    private FindingsComparisonResult normalizeResult(FindingsComparisonResult result) {
        if (result == null) {
            throw new IllegalStateException("结构化结果解析失败");
        }

        FindingsComparisonResult normalized = new FindingsComparisonResult();
        normalized.setItems(normalizeItems(result.getItems()));
        normalized.setCommonPatterns(normalizeStrings(result.getCommonPatterns()));
        normalized.setMethodDifferences(normalizeStrings(result.getMethodDifferences()));
        normalized.setExperimentDifferences(normalizeStrings(result.getExperimentDifferences()));
        normalized.setComparisonBasis(normalizeStrings(result.getComparisonBasis()));
        normalized.setOverallSummary(StringUtils.hasText(result.getOverallSummary())
                ? result.getOverallSummary().trim()
                : "已完成论文发现对比");
        return normalized;
    }

    private List<PaperComparisonItem> normalizeItems(List<PaperComparisonItem> items) {
        if (CollectionUtils.isEmpty(items)) {
            return List.of();
        }
        List<PaperComparisonItem> normalizedItems = new ArrayList<>();
        for (PaperComparisonItem item : items) {
            if (item == null) {
                continue;
            }
            PaperComparisonItem normalized = new PaperComparisonItem();
            normalized.setPaperTitle(normalizeText(item.getPaperTitle()));
            normalized.setPaperId(normalizeText(item.getPaperId()));
            normalized.setMethodHighlights(normalizeStrings(item.getMethodHighlights()));
            normalized.setExperimentHighlights(normalizeStrings(item.getExperimentHighlights()));
            normalized.setStrengths(normalizeStrings(item.getStrengths()));
            normalized.setLimitations(normalizeStrings(item.getLimitations()));
            normalized.setNotableDifferences(normalizeStrings(item.getNotableDifferences()));
            if (hasUsableItem(normalized)) {
                normalizedItems.add(normalized);
            }
        }
        return List.copyOf(normalizedItems);
    }

    private boolean hasUsableContent(FindingsComparisonResult result) {
        return result != null
                && (!CollectionUtils.isEmpty(result.getItems())
                        || !CollectionUtils.isEmpty(result.getCommonPatterns())
                        || !CollectionUtils.isEmpty(result.getMethodDifferences())
                        || !CollectionUtils.isEmpty(result.getExperimentDifferences())
                        || !CollectionUtils.isEmpty(result.getComparisonBasis())
                        || StringUtils.hasText(result.getOverallSummary()));
    }

    private boolean hasUsableItem(PaperComparisonItem item) {
        return StringUtils.hasText(item.getPaperTitle())
                || StringUtils.hasText(item.getPaperId())
                || !CollectionUtils.isEmpty(item.getMethodHighlights())
                || !CollectionUtils.isEmpty(item.getExperimentHighlights())
                || !CollectionUtils.isEmpty(item.getStrengths())
                || !CollectionUtils.isEmpty(item.getLimitations())
                || !CollectionUtils.isEmpty(item.getNotableDifferences());
    }

    private List<String> normalizeStrings(List<String> values) {
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

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
