package com.wwt.assistant.agent.tool.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.agent.model.dto.PaperMethodItem;
import com.wwt.assistant.agent.service.MethodExtractionService;
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
class ExtractMethodToolTest {

    @Mock
    private MethodExtractionService methodExtractionService;

    @InjectMocks
    private ExtractMethodTool extractMethodTool;

    @Test
    void shouldExtractMethodsAndWriteBackContext() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setOriginalQuestion("帮我提取这几篇论文的方法");
        context.setIntermediateResults(new LinkedHashMap<>());

        List<DocumentChunkMapper.QaChunkRecord> chunkRecords = List.of(
                buildChunkRecord(1L, 101L, "Paper A", 1, "Method", "Paper A uses a graph contrastive method."),
                buildChunkRecord(2L, 102L, "Paper B", 2, "Approach", "Paper B proposes a dual-encoder retrieval method."),
                buildChunkRecord(3L, 102L, "Paper B", 3, "Experiment", "The dual-encoder is trained with hard negatives."));
        context.getIntermediateResults().put("retrievedChunks", new ArrayList<>(chunkRecords));

        MethodExtractionResult extractionResult = new MethodExtractionResult();
        extractionResult.setItems(List.of(
                buildPaperMethodItem("Paper A", "101", "图对比学习方法"),
                buildPaperMethodItem("Paper B", "102", "双编码检索方法")));
        extractionResult.setOverallSummary("已提取两篇论文的方法信息");

        when(methodExtractionService.extractMethods(
                eq("帮我提取这几篇论文的方法"),
                eq(null),
                anyList()))
                .thenReturn(extractionResult);

        AgentStep step = new AgentStep();
        step.setStepNo(1);
        step.setAction("EXTRACT_METHOD");
        step.setToolName("extractMethod");
        step.setInputKey("retrievedChunks");
        step.setOutputKey("methods");

        AgentStepResult stepResult = extractMethodTool.execute(context, step);

        assertTrue(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("COMPLETED", stepResult.getStatus());
        assertSame(extractionResult, stepResult.getOutput());
        assertNotNull(context.getIntermediateResults().get("methods"));
        assertSame(extractionResult, context.getIntermediateResults().get("methods"));
        verify(methodExtractionService).extractMethods(
                eq("帮我提取这几篇论文的方法"),
                eq(null),
                anyList());
    }

    @Test
    void shouldFailWhenChunksAreMissing() {
        AgentExecutionContext context = new AgentExecutionContext();
        context.setIntermediateResults(new LinkedHashMap<>());

        AgentStep step = new AgentStep();
        step.setStepNo(2);
        step.setAction("EXTRACT_METHOD");
        step.setToolName("extractMethod");
        step.setInputKey("retrievedChunks");
        step.setOutputKey("methods");

        AgentStepResult stepResult = extractMethodTool.execute(context, step);

        assertFalse(Boolean.TRUE.equals(stepResult.getSuccess()));
        assertEquals("FAILED", stepResult.getStatus());
        assertEquals("缺少方法提取所需输入 chunks", stepResult.getErrorMessage());
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

    private PaperMethodItem buildPaperMethodItem(String paperTitle, String paperId, String methodSummary) {
        PaperMethodItem item = new PaperMethodItem();
        item.setPaperTitle(paperTitle);
        item.setPaperId(paperId);
        item.setMethodSummary(methodSummary);
        item.setMethodKeywords(List.of("keyword"));
        item.setRawEvidenceSnippets(List.of("evidence"));
        return item;
    }
}
