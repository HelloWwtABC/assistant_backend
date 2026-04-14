package com.wwt.assistant.agent.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FinalAnswerSummaryResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.service.FinalAnswerSummaryAiService;
import com.wwt.assistant.agent.service.FinalAnswerSummaryService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 基于 LangChain4j 的最终答案总结服务实现。
 */
@Service
public class FinalAnswerSummaryServiceImpl implements FinalAnswerSummaryService {

    private final ObjectMapper objectMapper;
    private final FinalAnswerSummaryAiService aiService;

    public FinalAnswerSummaryServiceImpl(
            @Qualifier("planningAgentChatModel") ChatModel planningAgentChatModel,
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.aiService = AiServices.create(FinalAnswerSummaryAiService.class, planningAgentChatModel);
    }

    @Override
    public FinalAnswerSummaryResult summarizeFinalAnswer(
            String originalQuestion,
            String rewrittenQuestion,
            FindingsComparisonResult comparisonResult,
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult) {
        if (comparisonResult == null && methodResult == null && experimentResult == null) {
            throw new IllegalArgumentException("缺少最终答案生成所需的中间结果");
        }

        try {
            FinalAnswerSummaryResult result = aiService.summarizeFinalAnswer(
                    resolveQuestion(originalQuestion, rewrittenQuestion),
                    buildSummaryPayload(comparisonResult, methodResult, experimentResult));
            FinalAnswerSummaryResult normalized = normalizeResult(result);
            if (!StringUtils.hasText(normalized.getFinalAnswer())) {
                throw new IllegalStateException("结构化结果为空，未生成可用的最终答案");
            }
            return normalized;
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("最终答案生成模型调用失败", ex);
        }
    }

    private String resolveQuestion(String originalQuestion, String rewrittenQuestion) {
        if (StringUtils.hasText(rewrittenQuestion)) {
            return rewrittenQuestion.trim();
        }
        if (StringUtils.hasText(originalQuestion)) {
            return originalQuestion.trim();
        }
        return "请基于现有分析结果生成最终答案";
    }

    private String buildSummaryPayload(
            FindingsComparisonResult comparisonResult,
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        if (comparisonResult != null) {
            root.set("comparisonFindings", objectMapper.valueToTree(comparisonResult));
        } else {
            root.putNull("comparisonFindings");
        }
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

    private FinalAnswerSummaryResult normalizeResult(FinalAnswerSummaryResult result) {
        if (result == null) {
            throw new IllegalStateException("结构化结果解析失败");
        }
        FinalAnswerSummaryResult normalized = new FinalAnswerSummaryResult();
        normalized.setFinalAnswer(StringUtils.hasText(result.getFinalAnswer()) ? result.getFinalAnswer().trim() : null);
        normalized.setAnswerOutline(normalizeStrings(result.getAnswerOutline()));
        normalized.setHighlights(normalizeStrings(result.getHighlights()));
        normalized.setWarnings(normalizeStrings(result.getWarnings()));
        normalized.setBasedOn(normalizeStrings(result.getBasedOn()));
        return normalized;
    }

    private List<String> normalizeStrings(List<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return List.of();
        }
        LinkedHashSet<String> normalizedValues = new LinkedHashSet<>();
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                normalizedValues.add(value.trim());
            }
        }
        return List.copyOf(normalizedValues);
    }
}
