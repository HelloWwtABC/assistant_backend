package com.wwt.assistant.agent.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.wwt.assistant.agent.model.AgentComplexity;
import org.junit.jupiter.api.Test;

class ComplexityJudgeServiceImplTest {

    private final ComplexityJudgeServiceImpl complexityJudgeService = new ComplexityJudgeServiceImpl();

    @Test
    void shouldJudgeSinglePaperMethodQuestionAsSimple() {
        AgentComplexity complexity = complexityJudgeService.judge("这篇论文的方法是什么");
        assertEquals(AgentComplexity.SIMPLE, complexity);
    }

    @Test
    void shouldJudgeThreePaperComparisonAsComplex() {
        AgentComplexity complexity = complexityJudgeService.judge("帮我对比三篇论文的方法和实验结果");
        assertEquals(AgentComplexity.COMPLEX, complexity);
    }
}
