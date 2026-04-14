package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import java.util.List;

/**
 * 负责从已检索论文分片中提取实验设置与实验结果。
 */
public interface ExperimentExtractionService {

    /**
     * 从已检索到的论文分片中提取实验设置与实验结果。
     */
    ExperimentExtractionResult extractExperiments(
            String originalQuestion,
            String rewrittenQuestion,
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords);
}
