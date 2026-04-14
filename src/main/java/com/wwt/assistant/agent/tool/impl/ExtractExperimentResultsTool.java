package com.wwt.assistant.agent.tool.impl;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.service.ExperimentExtractionService;
import com.wwt.assistant.agent.tool.AgentTool;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 从已检索论文分片中提取实验设置与实验结果的工具实现。
 */
@Component
public class ExtractExperimentResultsTool implements AgentTool {

    private static final String TOOL_NAME = "extractExperimentResults";
    private static final String DEFAULT_OUTPUT_KEY = "experimentResults";
    private static final String STEP_STATUS_COMPLETED = "COMPLETED";
    private static final String STEP_STATUS_FAILED = "FAILED";
    private static final String MISSING_INPUT_MESSAGE = "缺少实验提取所需输入 chunks";

    private final ExperimentExtractionService experimentExtractionService;

    public ExtractExperimentResultsTool(ExperimentExtractionService experimentExtractionService) {
        this.experimentExtractionService = experimentExtractionService;
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
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords = resolveChunkRecords(context, step);
            if (CollectionUtils.isEmpty(chunkRecords)) {
                return fail(result, MISSING_INPUT_MESSAGE, "实验提取失败：缺少输入分片", startNanos);
            }

            ExperimentExtractionResult extractionResult = experimentExtractionService.extractExperiments(
                    context == null ? null : context.getOriginalQuestion(),
                    context == null ? null : context.getRewrittenQuestion(),
                    chunkRecords);
            if (extractionResult == null || CollectionUtils.isEmpty(extractionResult.getItems())) {
                return fail(result, "结构化结果为空，未提取到可用的实验信息", "实验提取失败：没有可用结果", startNanos);
            }

            writeBack(context, step, extractionResult);
            result.setSuccess(Boolean.TRUE);
            result.setStatus(STEP_STATUS_COMPLETED);
            result.setOutput(extractionResult);
            result.setSummary("已从 " + extractionResult.getItems().size() + " 篇论文片段中提取实验信息");
            result.setLatencyMs(calculateLatencyMs(startNanos));
            result.setRetryCount(0);
            return result;
        } catch (Exception ex) {
            return fail(result, ex.getMessage(), "实验提取失败", startNanos);
        }
    }

    private List<DocumentChunkMapper.QaChunkRecord> resolveChunkRecords(AgentExecutionContext context, AgentStep step) {
        Object candidate = null;
        if (step != null && StringUtils.hasText(step.getInputKey())) {
            Map<String, Object> intermediateResults = context == null ? null : context.getIntermediateResults();
            candidate = intermediateResults == null ? null : intermediateResults.get(step.getInputKey());
        }
        if (candidate == null && context != null) {
            candidate = context.getRetrievedChunks();
        }
        if (candidate == null) {
            return List.of();
        }
        if (!(candidate instanceof List<?> inputList)) {
            throw new IllegalStateException("实验提取输入不是有效的 chunks 列表");
        }
        if (inputList.isEmpty()) {
            return List.of();
        }

        List<DocumentChunkMapper.QaChunkRecord> chunkRecords = new ArrayList<>(inputList.size());
        for (Object item : inputList) {
            if (!(item instanceof DocumentChunkMapper.QaChunkRecord chunkRecord)) {
                throw new IllegalStateException("实验提取输入类型不受支持，期望为 QaChunkRecord 列表");
            }
            chunkRecords.add(chunkRecord);
        }
        return chunkRecords;
    }

    private void writeBack(AgentExecutionContext context, AgentStep step, ExperimentExtractionResult extractionResult) {
        if (context == null) {
            return;
        }
        String outputKey = step != null && StringUtils.hasText(step.getOutputKey())
                ? step.getOutputKey().trim()
                : DEFAULT_OUTPUT_KEY;
        ensureIntermediateResults(context).put(outputKey, extractionResult);
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

    private AgentStepResult fail(AgentStepResult result, String errorMessage, String summary, long startNanos) {
        result.setSuccess(Boolean.FALSE);
        result.setStatus(STEP_STATUS_FAILED);
        result.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage : "实验提取失败");
        result.setSummary(summary);
        result.setLatencyMs(calculateLatencyMs(startNanos));
        result.setRetryCount(0);
        return result;
    }

    private long calculateLatencyMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
