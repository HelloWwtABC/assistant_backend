package com.wwt.assistant.agent.service;

import com.wwt.assistant.agent.model.AgentComplexity;

/**
 * 用于判断问题复杂度的服务。
 */
public interface ComplexityJudgeService {

    AgentComplexity judge(String question);
}
