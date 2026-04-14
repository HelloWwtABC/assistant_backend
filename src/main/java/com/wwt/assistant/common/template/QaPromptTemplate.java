package com.wwt.assistant.common.template;

import java.util.List;
import java.util.StringJoiner;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
public class QaPromptTemplate {

    private static final String REWRITE_SYSTEM_PROMPT = """
            You are a query rewrite assistant for enterprise RAG.
            Rewrite the current user question into a standalone retrieval query when it depends on conversation history.
            Keep the original intent and constraints.
            Do not answer the question.
            Output only the rewritten standalone question.
            If the current question is already standalone, output it directly.
            """;

    private static final String ANSWER_SYSTEM_PROMPT = """
            You are an enterprise knowledge-base QA assistant.
            Answer strictly based on the retrieved knowledge chunks provided by the system.
            Do not fabricate facts, APIs, rules, processes, or conclusions.
            If the retrieved context is insufficient, explicitly say that the answer cannot be confirmed from the current knowledge base.
            Reply in the same language as the user's question.
            Conversation history is only for understanding follow-up intent, not for citation evidence.
            """;

    public String buildRewriteSystemPrompt() {
        return REWRITE_SYSTEM_PROMPT;
    }

    public String buildRewriteUserPrompt(String question, List<String> historyBlocks) {
        return """
                [Current Question]
                %s

                [Recent Conversation]
                %s

                [Task]
                Rewrite the current question into a standalone retrieval query.
                Output only the rewritten question.
                """.formatted(normalizeText(question), buildSection(historyBlocks, "No recent conversation."));
    }

    public String buildAnswerSystemPrompt() {
        return ANSWER_SYSTEM_PROMPT;
    }

    public String buildAnswerUserPrompt(String question, List<String> contextBlocks, List<String> historyBlocks) {
        return """
                [Current Question]
                %s

                [Retrieved Knowledge Chunks]
                %s

                [Recent Conversation For Intent]
                %s

                [Answer Rules]
                1. Use retrieved knowledge chunks as the only evidence source.
                2. If evidence is insufficient, say so clearly.
                3. Give the conclusion first, then add necessary explanation.
                4. Do not cite or rely on conversation history as evidence.
                """.formatted(
                normalizeText(question),
                buildSection(contextBlocks, "No relevant knowledge chunks were retrieved."),
                buildSection(historyBlocks, "No conversation history is needed for this question."));
    }

    private String buildSection(List<String> blocks, String emptyText) {
        if (CollectionUtils.isEmpty(blocks)) {
            return emptyText;
        }

        StringJoiner joiner = new StringJoiner(System.lineSeparator() + System.lineSeparator());
        int index = 1;
        for (String block : blocks) {
            String normalized = normalizeText(block);
            if (!StringUtils.hasText(normalized)) {
                continue;
            }
            joiner.add("Block " + index + ":" + System.lineSeparator() + normalized);
            index++;
        }
        return joiner.length() == 0 ? emptyText : joiner.toString();
    }

    private String normalizeText(String text) {
        return StringUtils.hasText(text) ? text.trim() : "";
    }
}
