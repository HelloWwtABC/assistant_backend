package com.wwt.assistant.agent.model;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示单个步骤执行后的结果快照。
 */
@Data
@NoArgsConstructor
public class AgentStepResult {

    /** 步骤序号。 */
    private Integer stepNo;

    /** 动作类型。 */
    private String action;

    /** 是否执行成功。 */
    private Boolean success;

    /** 步骤状态。 */
    private String status;

    /** 步骤输出。 */
    private Object output;

    /** 结果摘要。 */
    private String summary;

    /** 错误信息。 */
    private String errorMessage;

    /** 执行耗时。 */
    private Long latencyMs;

    /** 重试次数。 */
    private Integer retryCount;
}
