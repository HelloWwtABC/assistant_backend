package com.wwt.assistant.agent.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示最终答案汇总后的结构化结果。
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FinalAnswerSummaryResult {

    /** 面向用户的最终回答。 */
    private String finalAnswer;

    /** 回答提纲。 */
    private List<String> answerOutline;

    /** 关键结论。 */
    private List<String> highlights;

    /** 风险或信息不足提示。 */
    private List<String> warnings;

    /** 本次回答基于的中间结果类型。 */
    private List<String> basedOn;
}
