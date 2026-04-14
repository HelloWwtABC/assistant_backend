package com.wwt.assistant.agent.model;

/**
 * Agent任务类型枚举。
 */
public enum AgentTaskType {
    SIMPLE_QA, // 简单问题
    PAPER_COMPARISON, // 多论文对比
    DOCUMENT_SUMMARY, // 文档总结
    MULTI_DOC_ANALYSIS, // 多文档综合分析
    UNKNOWN // 无法明确归类
}
