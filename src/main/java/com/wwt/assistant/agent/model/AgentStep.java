package com.wwt.assistant.agent.model;

import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 表示规划中的单个执行步骤。
 */
@Data
@NoArgsConstructor
public class AgentStep {

    /** 步骤序号。 */
    private Integer stepNo;

    /** 步骤动作类型。 */
    private String action;

    /** 当前步骤目标。 */
    private String goal;

    /** 关联工具名称。 */
    private String toolName;

    /** 输入键。 */
    private String inputKey;

    /** 输出键。 */
    private String outputKey;

    /** 依赖的前置步骤编号。 */
    private List<Integer> dependsOnSteps;

    /** 当前步骤状态。 */
    private String status;
}
