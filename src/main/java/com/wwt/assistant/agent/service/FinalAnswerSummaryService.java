package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.dto.ExperimentExtractionResult;
import com.wwt.assistant.agent.model.dto.FinalAnswerSummaryResult;
import com.wwt.assistant.agent.model.dto.FindingsComparisonResult;
import com.wwt.assistant.agent.model.dto.MethodExtractionResult;

/**
 * 负责基于中间结果生成最终答案。
 */
public interface FinalAnswerSummaryService {

    /**
     * 基于 comparison 或其它中间结果生成最终答案。
     */
    FinalAnswerSummaryResult summarizeFinalAnswer(
            String originalQuestion,
            String rewrittenQuestion,
            FindingsComparisonResult comparisonResult,
            MethodExtractionResult methodResult,
            ExperimentExtractionResult experimentResult);
}
