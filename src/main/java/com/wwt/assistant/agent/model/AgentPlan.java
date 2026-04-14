package com.wwt.assistant.agent.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示一次Planning Agent生成的整体执行计划。
 */
@Data
@NoArgsConstructor
public class AgentPlan {

    /** 任务类型。 */
    private AgentTaskType taskType;

    /** 任务复杂度。 */
    private AgentComplexity complexity;

    /** 用户原始问题。 */
    private String originalQuestion;

    /** 改写后的问题。 */
    private String rewrittenQuestion;

    /** 计划摘要。 */
    private String planSummary;

    /** 执行步骤列表。 */
    private List<AgentStep> steps;

    /** 最大步骤数。 */
    private Integer maxStepCount;

    /** 最大检索轮数。 */
    private Integer maxRetrievalRound;

    /** 计划状态。 */
    private String status;
}
