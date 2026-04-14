package com.wwt.assistant.agent.tool.impl;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FinalAnswerSummaryResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.service.FinalAnswerSummaryService;
import com.wwt.assistant.agent.tool.AgentTool;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于 comparison 或其它中间结果生成最终答案的工具实现。
 */
@Component
public class SummarizeFinalAnswerTool implements AgentTool {

    private static final String TOOL_NAME = "summarizeFinalAnswer";
    private static final String DEFAULT_OUTPUT_KEY = "finalAnswer";
    private static final String STEP_STATUS_COMPLETED = "COMPLETED";
    private static final String STEP_STATUS_FAILED = "FAILED";
    private static final String MISSING_INPUT_MESSAGE = "缺少最终答案生成所需的中间结果";

    private final FinalAnswerSummaryService finalAnswerSummaryService;

    public SummarizeFinalAnswerTool(FinalAnswerSummaryService finalAnswerSummaryService) {
        this.finalAnswerSummaryService = finalAnswerSummaryService;
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
            FinalAnswerInput input = resolveInput(context, step);
            if (input.comparisonResult() == null && input.methodResult() == null && input.experimentResult() == null) {
                return fail(result, MISSING_INPUT_MESSAGE, "最终答案生成失败：缺少输入结果", startNanos);
            }

            FinalAnswerSummaryResult summaryResult = finalAnswerSummaryService.summarizeFinalAnswer(
                    context == null ? null : context.getOriginalQuestion(),
                    context == null ? null : context.getRewrittenQuestion(),
                    input.comparisonResult(),
                    input.methodResult(),
                    input.experimentResult());

            if (summaryResult == null || !StringUtils.hasText(summaryResult.getFinalAnswer())) {
                return fail(result, "结构化结果为空，未生成可用的最终答案", "最终答案生成失败：没有可用结果", startNanos);
            }

            writeBack(context, step, summaryResult);
            result.setSuccess(Boolean.TRUE);
            result.setStatus(STEP_STATUS_COMPLETED);
            result.setOutput(summaryResult);
            result.setSummary(buildSummary(input));
            result.setLatencyMs(calculateLatencyMs(startNanos));
            result.setRetryCount(0);
            return result;
        } catch (Exception ex) {
            return fail(result, ex.getMessage(), "最终答案生成失败", startNanos);
        }
    }

    private FinalAnswerInput resolveInput(AgentExecutionContext context, AgentStep step) {
        Map<String, Object> intermediateResults = context == null ? null : context.getIntermediateResults();
        FindingsComparisonResult comparisonResult = null;
        MethodExtractionResult methodResult = null;
        ExperimentExtractionResult experimentResult = null;

        if (step != null && StringUtils.hasText(step.getInputKey()) && intermediateResults != null) {
            Object candidate = intermediateResults.get(step.getInputKey());
            if (candidate instanceof Map<?, ?> inputMap) {
                comparisonResult = asComparisonResult(inputMap.get("comparisonFindings"));
                methodResult = asMethodResult(inputMap.get("methods"));
                experimentResult = asExperimentResult(inputMap.get("experimentResults"));
            } else {
                comparisonResult = asComparisonResult(candidate);
            }
        }

        if (comparisonResult == null && intermediateResults != null) {
            comparisonResult = asComparisonResult(intermediateResults.get("comparisonFindings"));
        }
        if (methodResult == null && intermediateResults != null) {
            methodResult = asMethodResult(intermediateResults.get("methods"));
        }
        if (experimentResult == null && intermediateResults != null) {
            experimentResult = asExperimentResult(intermediateResults.get("experimentResults"));
        }

        return new FinalAnswerInput(comparisonResult, methodResult, experimentResult);
    }

    private FindingsComparisonResult asComparisonResult(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof FindingsComparisonResult findingsComparisonResult) {
            return findingsComparisonResult;
        }
        throw new IllegalStateException("comparisonFindings 输入类型不受支持，期望为 FindingsComparisonResult");
    }

    private MethodExtractionResult asMethodResult(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof MethodExtractionResult methodExtractionResult) {
            return methodExtractionResult;
        }
        throw new IllegalStateException("methods 输入类型不受支持，期望为 MethodExtractionResult");
    }

    private ExperimentExtractionResult asExperimentResult(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof ExperimentExtractionResult experimentExtractionResult) {
            return experimentExtractionResult;
        }
        throw new IllegalStateException("experimentResults 输入类型不受支持，期望为 ExperimentExtractionResult");
    }

    private void writeBack(AgentExecutionContext context, AgentStep step, FinalAnswerSummaryResult summaryResult) {
        if (context == null) {
            return;
        }
        String outputKey = step != null && StringUtils.hasText(step.getOutputKey())
                ? step.getOutputKey().trim()
                : DEFAULT_OUTPUT_KEY;
        ensureIntermediateResults(context).put(outputKey, summaryResult);
        context.setFinalDraftAnswer(summaryResult.getFinalAnswer());
    }

    private String buildSummary(FinalAnswerInput input) {
        if (input.comparisonResult() != null) {
            return "已基于对比结果生成最终答案";
        }
        if (input.methodResult() != null && input.experimentResult() != null) {
            return "已基于方法与实验结果生成最终答案";
        }
        if (input.methodResult() != null) {
            return "已基于方法结果生成最终答案";
        }
        return "已基于实验结果生成最终答案";
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
        result.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage : "最终答案生成失败");
        result.setSummary(summary);
        result.setLatencyMs(calculateLatencyMs(startNanos));
        result.setRetryCount(0);
        return result;
    }

    private long calculateLatencyMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record FinalAnswerInput(
            FindingsComparisonResult comparisonResult,
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult) {
    }
}
