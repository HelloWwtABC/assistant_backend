package com.wwt.assistant.agent.tool.impl;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.service.FindingsComparisonService;
import com.wwt.assistant.agent.tool.AgentTool;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于已提取的方法与实验结果做对比分析的工具实现。
 */
@Component
public class CompareFindingsTool implements AgentTool {

    private static final String TOOL_NAME = "compareFindings";
    private static final String DEFAULT_OUTPUT_KEY = "comparisonFindings";
    private static final String STEP_STATUS_COMPLETED = "COMPLETED";
    private static final String STEP_STATUS_FAILED = "FAILED";
    private static final String MISSING_INPUT_MESSAGE = "缺少对比所需的 methods 和 experimentResults";

    private final FindingsComparisonService findingsComparisonService;

    public CompareFindingsTool(FindingsComparisonService findingsComparisonService) {
        this.findingsComparisonService = findingsComparisonService;
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
            ComparisonInput comparisonInput = resolveComparisonInput(context, step);
            if (comparisonInput.methodResult() == null && comparisonInput.experimentResult() == null) {
                return fail(result, MISSING_INPUT_MESSAGE, "对比分析失败：缺少输入结果", startNanos);
            }

            FindingsComparisonResult comparisonResult = findingsComparisonService.compareFindings(
                    context == null ? null : context.getOriginalQuestion(),
                    context == null ? null : context.getRewrittenQuestion(),
                    comparisonInput.methodResult(),
                    comparisonInput.experimentResult());

            if (comparisonResult == null) {
                return fail(result, "结构化结果为空，未生成可用的对比信息", "对比分析失败：没有可用结果", startNanos);
            }

            writeBack(context, step, comparisonResult);
            result.setSuccess(Boolean.TRUE);
            result.setStatus(STEP_STATUS_COMPLETED);
            result.setOutput(comparisonResult);
            result.setSummary(buildSummary(comparisonInput, comparisonResult));
            result.setLatencyMs(calculateLatencyMs(startNanos));
            result.setRetryCount(0);
            return result;
        } catch (Exception ex) {
            return fail(result, ex.getMessage(), "对比分析失败", startNanos);
        }
    }

    private ComparisonInput resolveComparisonInput(AgentExecutionContext context, AgentStep step) {
        Map<String, Object> intermediateResults = context == null ? null : context.getIntermediateResults();
        MethodExtractionResult methodResult = null;
        ExperimentExtractionResult experimentResult = null;

        if (step != null && StringUtils.hasText(step.getInputKey()) && intermediateResults != null) {
            Object candidate = intermediateResults.get(step.getInputKey());
            if (candidate instanceof Map<?, ?> inputMap) {
                methodResult = asMethodResult(inputMap.get("methods"));
                experimentResult = asExperimentResult(inputMap.get("experimentResults"));
            }
        }

        if (methodResult == null && intermediateResults != null) {
            methodResult = asMethodResult(intermediateResults.get("methods"));
        }
        if (experimentResult == null && intermediateResults != null) {
            experimentResult = asExperimentResult(intermediateResults.get("experimentResults"));
        }

        return new ComparisonInput(methodResult, experimentResult);
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

    private void writeBack(AgentExecutionContext context, AgentStep step, FindingsComparisonResult comparisonResult) {
        if (context == null) {
            return;
        }
        String outputKey = step != null && StringUtils.hasText(step.getOutputKey())
                ? step.getOutputKey().trim()
                : DEFAULT_OUTPUT_KEY;
        ensureIntermediateResults(context).put(outputKey, comparisonResult);
    }

    private String buildSummary(ComparisonInput input, FindingsComparisonResult comparisonResult) {
        int paperCount = comparisonResult.getItems() == null ? 0 : comparisonResult.getItems().size();
        if (input.methodResult() != null && input.experimentResult() != null) {
            return "已完成 " + paperCount + " 篇论文的方法与实验对比";
        }
        if (input.methodResult() != null) {
            return "已完成 " + paperCount + " 篇论文的方法对比";
        }
        return "已完成 " + paperCount + " 篇论文的实验对比";
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
        result.setErrorMessage(StringUtils.hasText(errorMessage) ? errorMessage : "对比分析失败");
        result.setSummary(summary);
        result.setLatencyMs(calculateLatencyMs(startNanos));
        result.setRetryCount(0);
        return result;
    }

    private long calculateLatencyMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private record ComparisonInput(
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult) {
    }
}
