package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;

/**
 * 负责对方法与实验提取结果做综合对比。
 */
public interface FindingsComparisonService {

    /**
     * 基于结构化方法结果和实验结果生成对比结果。
     */
    FindingsComparisonResult compareFindings(
            String originalQuestion,
            String rewrittenQuestion,
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult);
}
