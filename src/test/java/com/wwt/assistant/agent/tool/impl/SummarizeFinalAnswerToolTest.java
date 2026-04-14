package com.wwt.assistant.agent.tool.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.model.dto.FinalAnswerSummaryResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.service.FinalAnswerSummaryService;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SummarizeFinalAnswerToolTest {

    @Mock
    private FinalAnswerSummaryService finalAnswerSummaryService;

    @InjectMocks
    private SummarizeFinalAnswerTool tool;

    @Test
    void shouldSummarizeFinalAnswerAndWriteBackContext() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setOriginalQuestion("帮我对比三篇论文的方法和实验结果");
        context.setIntermediateResults(new LinkedHashMap<>());

        FindingsComparisonResult comparisonResult = new FindingsComparisonResult();
        context.getIntermediateResults().put("comparisonFindings", comparisonResult);

        FinalAnswerSummaryResult summaryResult = new FinalAnswerSummaryResult();
        summaryResult.setFinalAnswer("综合来看，三篇论文在方法设计和实验验证上各有侧重。");
        summaryResult.setHighlights(List.of("结论一", "结论二"));

        when(finalAnswerSummaryService.summarizeFinalAnswer(
                eq("帮我对比三篇论文的方法和实验结果"),
                eq(null),
                eq(comparisonResult),
                eq(null),
                eq(null)))
                .thenReturn(summaryResult);

        AgentStep step = new AgentStep();
        step.setStepNo(5);
        step.setAction("SUMMARIZE_FINAL_ANSWER");
        step.setToolName("summarizeFinalAnswer");
        step.setOutputKey("finalAnswer");

        AgentStepResult stepResult = tool.execute(context, step);

        assertTrue(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("COMPLETED", stepResult.getStatus());
        assertSame(summaryResult, stepResult.getOutput());
        assertSame(summaryResult, context.getIntermediateResults().get("finalAnswer"));
        assertEquals("综合来看，三篇论文在方法设计和实验验证上各有侧重。", context.getFinalDraftAnswer());
        verify(finalAnswerSummaryService).summarizeFinalAnswer(
                eq("帮我对比三篇论文的方法和实验结果"),
                eq(null),
                eq(comparisonResult),
                eq(null),
                eq(null));
    }

    @Test
    void shouldFailWhenAllInputsAreMissing() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setIntermediateResults(new LinkedHashMap<>());

        AgentStep step = new AgentStep();
        step.setStepNo(6);
        step.setAction("SUMMARIZE_FINAL_ANSWER");
        step.setToolName("summarizeFinalAnswer");

        AgentStepResult stepResult = tool.execute(context, step);

        assertFalse(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("FAILED", stepResult.getStatus());
        assertEquals("缺少最终答案生成所需的中间结果", stepResult.getErrorMessage());
    }
}
