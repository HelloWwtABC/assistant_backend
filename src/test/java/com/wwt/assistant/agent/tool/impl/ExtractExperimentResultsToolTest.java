package com.wwt.assistant.agent.tool.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.PaperExperimentItem;
import com.wwt.assistant.agent.service.ExperimentExtractionService;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExtractExperimentResultsToolTest {

    @Mock
    private ExperimentExtractionService experimentExtractionService;

    @InjectMocks
    private ExtractExperimentResultsTool tool;

    @Test
    void shouldExtractExperimentResultsAndWriteBack() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setOriginalQuestion("帮我提取这些论文的实验设置和结果");
        context.setIntermediateResults(new LinkedHashMap<>());

        List<DocumentChunkMapper.QaChunkRecord> chunks = List.of(
                buildChunkRecord(1L, 101L, "Paper A", 1, "Experiment", "Paper A evaluates on CIFAR-10 with accuracy."),
                buildChunkRecord(2L, 102L, "Paper B", 2, "Results", "Paper B compares with BERT and improves F1 by 2 points."));
        context.getIntermediateResults().put("retrievedChunks", new ArrayList<>(chunks));

        ExperimentExtractionResult extractionResult = new ExperimentExtractionResult();
        extractionResult.setItems(List.of(
                buildItem("Paper A", "101", "CIFAR-10", "accuracy"),
                buildItem("Paper B", "102", "MRPC", "F1")));
        extractionResult.setOverallSummary("已提取两篇论文的实验信息");

        when(experimentExtractionService.extractExperiments(eq("帮我提取这些论文的实验设置和结果"), eq(null), anyList()))
                .thenReturn(extractionResult);

        AgentStep step = new AgentStep();
        step.setStepNo(2);
        step.setAction("EXTRACT_EXPERIMENT_RESULTS");
        step.setToolName("extractExperimentResults");
        step.setInputKey("retrievedChunks");
        step.setOutputKey("experimentResults");

        AgentStepResult stepResult = tool.execute(context, step);

        assertTrue(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("COMPLETED", stepResult.getStatus());
        assertSame(extractionResult, stepResult.getOutput());
        assertSame(extractionResult, context.getIntermediateResults().get("experimentResults"));
        verify(experimentExtractionService).extractExperiments(eq("帮我提取这些论文的实验设置和结果"), eq(null), anyList());
    }

    @Test
    void shouldFailWhenChunksAreMissing() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setIntermediateResults(new LinkedHashMap<>());

        AgentStep step = new AgentStep();
        step.setStepNo(3);
        step.setAction("EXTRACT_EXPERIMENT_RESULTS");
        step.setToolName("extractExperimentResults");
        step.setInputKey("retrievedChunks");

        AgentStepResult stepResult = tool.execute(context, step);

        assertFalse(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("FAILED", stepResult.getStatus());
        assertEquals("缺少实验提取所需输入 chunks", stepResult.getErrorMessage());
    }

    private DocumentChunkMapper.QaChunkRecord buildChunkRecord(
            Long chunkId,
            Long documentId,
            String documentName,
            Integer chunkIndex,
            String sectionTitle,
            String content) {
        DocumentChunkMapper.QaChunkRecord record = new DocumentChunkMapper.QaChunkRecord();
        record.setChunkId(chunkId);
        record.setDocumentId(documentId);
        record.setDocumentName(documentName);
        record.setChunkIndex(chunkIndex);
        record.setSectionTitle(sectionTitle);
        record.setContent(content);
        return record;
    }

    private PaperExperimentItem buildItem(String paperTitle, String paperId, String dataset, String metric) {
        PaperExperimentItem item = new PaperExperimentItem();
        item.setPaperTitle(paperTitle);
        item.setPaperId(paperId);
        item.setExperimentSetupSummary("实验设置摘要");
        item.setDatasetNames(List.of(dataset));
        item.setMetricNames(List.of(metric));
        item.setBaselineModels(List.of("baseline"));
        item.setResultSummary("实验结果摘要");
        item.setKeyFindings(List.of("关键发现"));
        item.setRawEvidenceSnippets(List.of("证据片段"));
        return item;
    }
}
