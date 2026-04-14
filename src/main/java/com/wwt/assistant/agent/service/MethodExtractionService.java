package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.MethodExtractionResult;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import java.util.List;

/**
 * 负责从已检索论文分片中提取方法信息。
 */
public interface MethodExtractionService {

    /**
     * 从已检索到的论文分片中提取每篇论文的方法信息。
     */
    MethodExtractionResult extractMethods(
            String originalQuestion,
            String rewrittenQuestion,
            List<DocumentChunkMapper.QaChunkRecord> chunkRecords);
}
