package com.wwt.assistant.agent.tool;

import com.wwt.assistant.agent.model.AgentExecutionContext;
import com.wwt.assistant.agent.model.AgentStep;
import com.wwt.assistant.agent.model.AgentStepResult;

/**
 * Planning Agent 可执行工具的统一抽象接口。
 */
public interface AgentTool {

    /**
     * 返回工具唯一名称，作为注册表中的唯一键。
     */
    String getToolName();

    /**
     * 判断当前工具是否支持执行该步骤。
     */
    default boolean supports(AgentStep step) {
        return step != null && getToolName().equals(step.getToolName());
    }

    /**
     * 执行工具逻辑。
     */
    AgentStepResult execute(AgentExecutionContext context, AgentStep step);
}
