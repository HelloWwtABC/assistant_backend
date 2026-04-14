package com.wwt.assistant.agent.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示Planning Agent执行完成后的统一输出模型。
 */
@Data
@NoArgsConstructor
public class AgentFinalResponse {

    /** 任务复杂度。 */
    private AgentComplexity complexity;

    /** 任务类型。 */
    private AgentTaskType taskType;

    /** 是否执行成功。 */
    private Boolean success;

    /** 最终答案。 */
    private String finalAnswer;

    /** 执行计划。 */
    private AgentPlan plan;

    /** 执行轨迹摘要。 */
    private String traceSummary;

    /** 警告信息。 */
    private String warningMessage;

    /** 总执行耗时。 */
    private Long executionTimeMs;

    /** 引用信息。 */
    private List<Object> citations;
}
