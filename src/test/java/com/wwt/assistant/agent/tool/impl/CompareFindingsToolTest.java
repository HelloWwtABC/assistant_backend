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
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.model.dto.PaperComparisonItem;
import com.wwt.assistant.agent.service.FindingsComparisonService;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompareFindingsToolTest {

    @Mock
    private FindingsComparisonService findingsComparisonService;

    @InjectMocks
    private CompareFindingsTool tool;

    @Test
    void shouldCompareMethodsAndExperimentsAndWriteBack() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setOriginalQuestion("帮我对比三篇论文的方法和实验结果");
        context.setIntermediateResults(new LinkedHashMap<>());

        MethodExtractionResult methodResult = new MethodExtractionResult();
        ExperimentExtractionResult experimentResult = new ExperimentExtractionResult();
        context.getIntermediateResults().put("methods", methodResult);
        context.getIntermediateResults().put("experimentResults", experimentResult);

        FindingsComparisonResult comparisonResult = new FindingsComparisonResult();
        comparisonResult.setItems(List.of(buildItem("Paper A", "101"), buildItem("Paper B", "102")));
        comparisonResult.setOverallSummary("已完成两篇论文的综合对比");

        when(findingsComparisonService.compareFindings(
                eq("帮我对比三篇论文的方法和实验结果"),
                eq(null),
                eq(methodResult),
                eq(experimentResult)))
                .thenReturn(comparisonResult);

        AgentStep step = new AgentStep();
        step.setStepNo(3);
        step.setAction("COMPARE_FINDINGS");
        step.setToolName("compareFindings");
        step.setOutputKey("comparisonFindings");

        AgentStepResult stepResult = tool.execute(context, step);

        assertTrue(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("COMPLETED", stepResult.getStatus());
        assertSame(comparisonResult, stepResult.getOutput());
        assertSame(comparisonResult, context.getIntermediateResults().get("comparisonFindings"));
        verify(findingsComparisonService).compareFindings(
                eq("帮我对比三篇论文的方法和实验结果"),
                eq(null),
                eq(methodResult),
                eq(experimentResult));
    }

    @Test
    void shouldFailWhenBothInputsAreMissing() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setIntermediateResults(new LinkedHashMap<>());

        AgentStep step = new AgentStep();
        step.setStepNo(4);
        step.setAction("COMPARE_FINDINGS");
        step.setToolName("compareFindings");

        AgentStepResult stepResult = tool.execute(context, step);

        assertFalse(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("FAILED", stepResult.getStatus());
        assertEquals("缺少对比所需的 methods 和 experimentResults", stepResult.getErrorMessage());
    }

    private PaperComparisonItem buildItem(String paperTitle, String paperId) {
        PaperComparisonItem item = new PaperComparisonItem();
        item.setPaperTitle(paperTitle);
        item.setPaperId(paperId);
        item.setMethodHighlights(List.of("方法亮点"));
        item.setExperimentHighlights(List.of("实验亮点"));
        item.setStrengths(List.of("优势"));
        item.setLimitations(List.of("局限"));
        item.setNotableDifferences(List.of("差异"));
        return item;
    }
}
