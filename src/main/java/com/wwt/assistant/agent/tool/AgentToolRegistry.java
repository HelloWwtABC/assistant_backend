package com.wwt.assistant.agent.tool;

import com.wwt.assistant.agent.model.AgentStep;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Agent 工具注册表，负责统一收集、索引和解析工具。
 */
@Component
public class AgentToolRegistry {

    private final Map<String, AgentTool> toolIndex;

    public AgentToolRegistry(List<AgentTool> agentTools) {
        List<AgentTool> tools = agentTools == null ? List.of() : agentTools;
        this.toolIndex = Collections.unmodifiableMap(buildToolIndex(tools));
    }

    /**
     * 按 toolName 获取工具，不存在时返回空。
     */
    public Optional<AgentTool> getTool(String toolName) {
        if (!StringUtils.hasText(toolName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(toolIndex.get(normalize(toolName)));
    }

    /**
     * 按 toolName 获取工具，不存在时抛异常。
     */
    public AgentTool getRequiredTool(String toolName) {
        return getTool(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Agent tool not found: " + toolName));
    }

    /**
     * 根据步骤解析工具，优先使用 toolName
     */
    public Optional<AgentTool> resolveTool(AgentStep step) {
        if (step == null) {
            return Optional.empty();
        }

        if (StringUtils.hasText(step.getToolName())) {
            return getTool(step.getToolName());
        }

        List<AgentTool> matchedTools = toolIndex.values().stream()
                .filter(tool -> tool.supports(step))
                .toList();
        if (matchedTools.isEmpty()) {
            return Optional.empty();
        }
        if (matchedTools.size() > 1) {
            throw new IllegalStateException("Multiple agent tools match step action: " + step.getAction());
        }
        return Optional.of(matchedTools.getFirst());
    }

    /**
     * 根据步骤解析工具，不存在时抛异常。
     */
    public AgentTool resolveRequiredTool(AgentStep step) {
        return resolveTool(step)
                .orElseThrow(() -> new IllegalArgumentException("No agent tool resolved for step"));
    }

    /**
     * 判断指定工具是否已注册。
     */
    public boolean containsTool(String toolName) {
        return getTool(toolName).isPresent();
    }

    /**
     * 返回已注册的工具名称列表。
     */
    public List<String> listToolNames() {
        return List.copyOf(toolIndex.keySet());
    }

    /**
     * toolName:tool
     * @param tools
     * @return
     */
    private Map<String, AgentTool> buildToolIndex(List<AgentTool> tools) {
        Map<String, AgentTool> index = new LinkedHashMap<>();
        for (AgentTool tool : tools) {
            if (tool == null) {
                continue;
            }
            String toolName = normalize(tool.getToolName());
            if (!StringUtils.hasText(toolName)) {
                throw new IllegalStateException("Agent tool name must not be blank: " + tool.getClass().getName());
            }
            AgentTool existing = index.putIfAbsent(toolName, tool);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate agent tool name: " + toolName
                                + ", classes=" + existing.getClass().getName()
                                + " and " + tool.getClass().getName());
            }
        }
        return index;
    }

    private String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT);
    }
}
