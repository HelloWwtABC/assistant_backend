package com.wwt.assistant.agent.service.impl;

import com.wwt.assistant.agent.model.AgentComplexity;
import com.wwt.assistant.agent.service.ComplexityJudgeService;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 采用规则优先策略判断问题复杂度，并预留后续 LLM 扩展入口。
 */
@Service
public class ComplexityJudgeServiceImpl implements ComplexityJudgeService {

    private static final List<String> COMPLEX_INTENT_KEYWORDS =
            List.of("对比", "比较", "分析", "总结", "归纳", "区别", "异同", "综述");

    private static final List<String> MULTI_OBJECT_KEYWORDS =
            List.of("多篇", "多个", "多份", "几篇", "几份", "三篇", "两篇", "四篇", "五篇", "多篇论文", "多个文档", "多篇文章");

    private static final List<String> MULTI_DIMENSION_KEYWORDS =
            List.of("方法和实验结果", "方法+实验结果", "方法与实验结果", "模型和实验结果", "贡献和局限", "方法和结论");

    private static final Pattern MULTI_OBJECT_PATTERN =
            Pattern.compile("(两篇|三篇|四篇|五篇|多篇|多份|多个).{0,8}(论文|文章|文档|报告)");

    private static final Pattern MULTI_DIMENSION_PATTERN =
            Pattern.compile("(方法|实验结果|结论|贡献|优点|缺点).{0,6}(和|与|及|、|\\+).{0,6}(方法|实验结果|结论|贡献|优点|缺点)");

    @Override
    public AgentComplexity judge(String question) {
        String normalizedQuestion = normalize(question);
        if (!StringUtils.hasText(normalizedQuestion)) {
            return AgentComplexity.SIMPLE;
        }

        if (matchesComplexRule(normalizedQuestion)) {
            return AgentComplexity.COMPLEX;
        }

        AgentComplexity llmJudgement = judgeByLlm(normalizedQuestion);
        return llmJudgement != null ? llmJudgement : AgentComplexity.SIMPLE;
    }

    private boolean matchesComplexRule(String question) {
        return containsAny(question, COMPLEX_INTENT_KEYWORDS)
                || containsAny(question, MULTI_OBJECT_KEYWORDS)
                || containsAny(question, MULTI_DIMENSION_KEYWORDS)
                || MULTI_OBJECT_PATTERN.matcher(question).find()
                || MULTI_DIMENSION_PATTERN.matcher(question).find()
                || looksBeyondSingleRetrieval(question);
    }

    private boolean looksBeyondSingleRetrieval(String question) {
        return (question.contains("分别") && question.contains("总结"))
                || (question.contains("先") && question.contains("再"))
                || (question.contains("并且") && question.contains("分析"))
                || (question.contains("同时") && MULTI_DIMENSION_PATTERN.matcher(question).find());
    }

    private AgentComplexity judgeByLlm(String question) {
        return null;
    }

    private boolean containsAny(String question, List<String> keywords) {
        for (String keyword : keywords) {
            if (question.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String question) {
        if (!StringUtils.hasText(question)) {
            return "";
        }
        // \s = 匹配任意空白字符  + = 前面的字符至少出现 1 次，可多次
        return question.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
