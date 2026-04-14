package com.wwt.assistant.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Planning Agent 的 LangChain4j 运行配置。
 */
@Data
@Component
@ConfigurationProperties(prefix = "agent.langchain4j")
public class PlanningAgentLangChainProperties {

    /**
     * 是否在启动后执行本地 Ollama 连通性检查。
     */
    private boolean startupCheckEnabled = true;

    /**
     * 启动检查使用的提示词。
     */
    private String startupCheckPrompt = "Reply with OK only.";
}
