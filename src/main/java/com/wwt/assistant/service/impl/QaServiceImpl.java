package com.wwt.assistant.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.UserContextHolder;
import com.wwt.assistant.common.template.QaPromptTemplate;
import com.wwt.assistant.dto.model.ModelChatResult;
import com.wwt.assistant.dto.qa.request.QaChatRequest;
import com.wwt.assistant.dto.qa.response.QaChatResponse;
import com.wwt.assistant.dto.qa.response.QaCitationItem;
import com.wwt.assistant.dto.qa.response.QaMessageItem;
import com.wwt.assistant.dto.qa.response.QaSessionDetailResponse;
import com.wwt.assistant.dto.qa.response.QaSessionItem;
import com.wwt.assistant.dto.vector.ChunkSearchHit;
import com.wwt.assistant.entity.ChatMessage;
import com.wwt.assistant.entity.ChatSession;
import com.wwt.assistant.entity.RequestLog;
import com.wwt.assistant.mapper.ChatMessageMapper;
import com.wwt.assistant.mapper.ChatSessionMapper;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import com.wwt.assistant.mapper.RequestLogMapper;
import com.wwt.assistant.service.ArkModelService;
import com.wwt.assistant.service.OllamaService;
import com.wwt.assistant.service.QaService;
import com.wwt.assistant.service.QdrantVectorService;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class QaServiceImpl implements QaService {

    private static final String SESSION_STATUS_ACTIVE = "active";
    private static final String MESSAGE_ROLE_USER = "user";
    private static final String MESSAGE_ROLE_ASSISTANT = "assistant";
    private static final String REQUEST_STATUS_SUCCESS = "success";
    private static final String REQUEST_STATUS_FAILED = "failed";
    private static final String DEFAULT_SESSION_TITLE = "新建问答会话";
    private static final String DEFAULT_LAST_MESSAGE_PREVIEW = "暂无消息，点击右侧输入框开始提问";
    private static final String SYSTEM_FAILURE_ANSWER = "系统故障，请重试";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int RECENT_HISTORY_LIMIT = 8;
    private static final int RETRIEVAL_TOP_K = 5;
    private static final int MAX_PREVIEW_LENGTH = 120;
    private static final int MAX_TITLE_LENGTH = 32;
    private static final int MAX_CONTEXT_LENGTH = 1200;
    private static final int MAX_CITATION_SNIPPET_LENGTH = 200;
    private static final List<String> FOLLOW_UP_HINTS = List.of(
            "这个", "这个问题", "这个方案", "这个接口", "这个模型", "它", "它的", "那", "那么", "那这个",
            "那怎么", "那如果", "继续", "接着", "然后", "此外", "另外", "上面", "前面", "刚才",
            "上一轮", "详细说", "展开说", "具体一点", "为什么", "怎么做", "怎么处理", "能不能", "是否可以");

    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final RequestLogMapper requestLogMapper;
    private final ArkModelService arkModelService;
    private final OllamaService ollamaService;
    private final QdrantVectorService qdrantVectorService;
    private final QaPromptTemplate qaPromptTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public QaServiceImpl(
            ChatSessionMapper chatSessionMapper,
            ChatMessageMapper chatMessageMapper,
            DocumentChunkMapper documentChunkMapper,
            RequestLogMapper requestLogMapper,
            ArkModelService arkModelService,
            OllamaService ollamaService,
            QdrantVectorService qdrantVectorService,
            QaPromptTemplate qaPromptTemplate,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.chatSessionMapper = chatSessionMapper;
        this.chatMessageMapper = chatMessageMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.requestLogMapper = requestLogMapper;
        this.arkModelService = arkModelService;
        this.ollamaService = ollamaService;
        this.qdrantVectorService = qdrantVectorService;
        this.qaPromptTemplate = qaPromptTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public ApiResponse<List<QaSessionItem>> getSessions() {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }

        List<QaSessionItem> sessions = chatSessionMapper.selectQaSessions(teamId, userId);
        return ApiResponse.success(sessions);
    }

    @Override
    public ApiResponse<QaSessionDetailResponse> getSessionDetail(String sessionId) {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }
        if (!StringUtils.hasText(sessionId)) {
            return ApiResponse.fail(400, "会话ID不能为空");
        }

        Long sessionIdValue;
        try {
            sessionIdValue = Long.parseLong(sessionId.trim());
        } catch (NumberFormatException ex) {
            return ApiResponse.fail(400, "会话ID参数不合法");
        }

        try {
            QaSessionDetailResponse detail = chatSessionMapper.selectQaSessionDetail(teamId, userId, sessionIdValue);
            if (detail == null) {
                return ApiResponse.fail(404, "会话不存在");
            }

            List<ChatMessageMapper.QaMessageRecord> records = chatMessageMapper.selectQaMessagesBySessionId(sessionIdValue);
            detail.setMessages(buildQaMessages(records));
            return ApiResponse.success(detail);
        } catch (Exception ex) {
            log.error("Get qa session detail failed, teamId={}, userId={}, sessionId={}", teamId, userId, sessionIdValue, ex);
            return ApiResponse.fail(500, "加载会话详情失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<QaSessionDetailResponse> createSession() {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            ChatSession session = new ChatSession();
            session.setId(IdWorker.getId());
            session.setTeamId(teamId);
            session.setUserId(userId);
            session.setTitle(DEFAULT_SESSION_TITLE);
            session.setStatus(SESSION_STATUS_ACTIVE);
            session.setMessageCount(0);
            session.setQuestionCount(0);
            session.setLastMessagePreview(DEFAULT_LAST_MESSAGE_PREVIEW);
            session.setCreatedAt(now);
            session.setUpdatedAt(now);

            if (chatSessionMapper.insert(session) != 1) {
                return ApiResponse.fail(500, "新建会话失败");
            }

            return ApiResponse.success(buildSessionDetailResponse(session, List.of()));
        } catch (Exception ex) {
            log.error("Create qa session failed, teamId={}, userId={}", teamId, userId, ex);
            return ApiResponse.fail(500, "新建会话失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> deleteSession(String sessionId) {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }
        if (!StringUtils.hasText(sessionId)) {
            return ApiResponse.fail(400, "会话ID不能为空");
        }

        Long sessionIdValue;
        try {
            sessionIdValue = Long.parseLong(sessionId.trim());
        } catch (NumberFormatException ex) {
            return ApiResponse.fail(400, "会话ID参数不合法");
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            int affected = chatSessionMapper.softDeleteQaSession(teamId, userId, sessionIdValue, now, userId, now);
            if (affected != 1) {
                return ApiResponse.fail(404, "会话不存在");
            }
            return ApiResponse.success(null);
        } catch (Exception ex) {
            log.error("Delete qa session failed, teamId={}, userId={}, sessionId={}", teamId, userId, sessionIdValue, ex);
            return ApiResponse.fail(500, "删除会话失败");
        }
    }

    @Override
    public ApiResponse<QaChatResponse> chat(QaChatRequest request) {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }
        if (request == null || !StringUtils.hasText(request.getSessionId())) {
            return ApiResponse.fail(400, "会话ID不能为空");
        }
        if (request == null || !StringUtils.hasText(request.getQuestion())) {
            return ApiResponse.fail(400, "问题不能为空");
        }

        Long sessionIdValue;
        try {
            sessionIdValue = Long.parseLong(request.getSessionId().trim());
        } catch (NumberFormatException ex) {
            return ApiResponse.fail(400, "会话ID参数不合法");
        }

        String question = request.getQuestion().trim();
        ChatSession session = chatSessionMapper.selectOwnedActiveSession(teamId, userId, sessionIdValue);
        if (session == null) {
            return ApiResponse.fail(404, "会话不存在");
        }

        List<ChatMessageMapper.RecentMessageRecord> recentMessages = loadRecentMessages(sessionIdValue);
        boolean followUp = isFollowUpQuestion(question, session, recentMessages);
        List<String> historyBlocks = followUp ? buildHistoryBlocks(recentMessages) : List.of();
        String standaloneQuestion = rewriteQuestionIfNeeded(question, historyBlocks);

        LocalDateTime userMessageTime = LocalDateTime.now();
        ChatMessage userMessage = buildUserMessage(sessionIdValue, question, userMessageTime);
        String titleCandidate = shouldUseQuestionAsTitle(session) ? abbreviate(question, MAX_TITLE_LENGTH) : null;
        try {
            persistUserQuestionPhase(teamId, userId, session, userMessage, question, titleCandidate, userMessageTime);
        } catch (Exception ex) {
            log.error("Persist qa user question failed, teamId={}, userId={}, sessionId={}", teamId, userId, sessionIdValue, ex);
            return ApiResponse.fail(500, "发送消息失败");
        }

        ProcessingResult processingResult = processAssistantAnswer(teamId, userId, sessionIdValue, question, standaloneQuestion, historyBlocks);
        LocalDateTime assistantMessageTime = LocalDateTime.now();
        ChatMessage assistantMessage = buildAssistantMessage(sessionIdValue, processingResult, assistantMessageTime);
        RequestLog requestLog = buildRequestLog(sessionIdValue, userId, question, standaloneQuestion, processingResult, assistantMessageTime);
        try {
            persistAssistantPhase(teamId, userId, session, requestLog, assistantMessage, processingResult, assistantMessageTime);
        } catch (Exception ex) {
            log.error("Persist qa assistant answer failed, teamId={}, userId={}, sessionId={}", teamId, userId, sessionIdValue, ex);
            return ApiResponse.fail(500, "发送消息失败");
        }

        QaChatResponse response = new QaChatResponse();
        response.setSessionId(String.valueOf(sessionIdValue));
        response.setUserMessage(buildMessageItem(userMessage, List.of()));
        response.setAssistantMessage(buildMessageItem(assistantMessage, processingResult.citations()));
        response.setUpdatedSession(buildSessionItem(session));
        return ApiResponse.success(response);
    }

    private List<QaMessageItem> buildQaMessages(List<ChatMessageMapper.QaMessageRecord> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }

        List<QaMessageItem> messages = new ArrayList<>(records.size());
        for (ChatMessageMapper.QaMessageRecord record : records) {
            QaMessageItem item = new QaMessageItem();
            item.setMessageId(record.getMessageId());
            item.setRole(record.getRole());
            item.setContent(record.getContent());
            item.setCreatedAt(record.getCreatedAt());
            item.setCitations(parseCitations(record.getCitationsJson()));
            messages.add(item);
        }
        return messages;
    }

    private List<QaCitationItem> parseCitations(String citationsJson) {
        if (!StringUtils.hasText(citationsJson)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(citationsJson);
            if (root == null || !root.isArray()) {
                return List.of();
            }

            List<QaCitationItem> citations = new ArrayList<>();
            for (JsonNode node : root) {
                if (node == null || node.isNull()) {
                    continue;
                }

                QaCitationItem item = new QaCitationItem();
                item.setCitationId(readText(node, "citationId", "citation_id", "id"));
                item.setDocumentId(readText(node, "documentId", "document_id"));
                item.setDocumentName(readText(node, "documentName", "document_name"));
                item.setChunkIndex(readInteger(node, "chunkIndex", "chunk_index"));
                item.setSnippet(readText(node, "snippet", "summary", "content"));

                if (!StringUtils.hasText(item.getCitationId())) {
                    item.setCitationId(buildFallbackCitationId(item, citations.size() + 1));
                }
                if (!StringUtils.hasText(item.getDocumentId())
                        && !StringUtils.hasText(item.getDocumentName())
                        && item.getChunkIndex() == null
                        && !StringUtils.hasText(item.getSnippet())) {
                    continue;
                }
                citations.add(item);
            }
            return citations;
        } catch (IOException ex) {
            log.warn("Failed to parse qa citations json", ex);
            return Collections.emptyList();
        }
    }

    private String buildFallbackCitationId(QaCitationItem item, int index) {
        if (StringUtils.hasText(item.getDocumentId())) {
            return item.getDocumentId() + "_" + index;
        }
        return "citation_" + index;
    }

    private String readText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value != null && !value.isNull()) {
                String text = value.asText();
                if (StringUtils.hasText(text)) {
                    return text.trim();
                }
            }
        }
        return null;
    }

    private Integer readInteger(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isInt() || value.isLong()) {
                return value.intValue();
            }
            String text = value.asText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private QaSessionDetailResponse buildSessionDetailResponse(ChatSession session, List<QaMessageItem> messages) {
        QaSessionDetailResponse detail = new QaSessionDetailResponse();
        detail.setSessionId(session.getId() == null ? null : String.valueOf(session.getId()));
        detail.setTitle(session.getTitle());
        detail.setCreatedAt(formatDateTime(session.getCreatedAt()));
        detail.setUpdatedAt(formatDateTime(session.getUpdatedAt()));
        detail.setLastMessagePreview(session.getLastMessagePreview());
        detail.setMessageCount(session.getMessageCount() == null ? 0 : session.getMessageCount());
        detail.setMessages(messages == null ? List.of() : messages);
        return detail;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME_FORMATTER);
    }

    private List<ChatMessageMapper.RecentMessageRecord> loadRecentMessages(Long sessionId) {
        List<ChatMessageMapper.RecentMessageRecord> records =
                chatMessageMapper.selectRecentMessages(sessionId, RECENT_HISTORY_LIMIT);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        records.sort(Comparator
                .comparing(ChatMessageMapper.RecentMessageRecord::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(ChatMessageMapper.RecentMessageRecord::getMessageId, Comparator.nullsLast(Long::compareTo)));
        return records;
    }

    private boolean isFollowUpQuestion(
            String question,
            ChatSession session,
            List<ChatMessageMapper.RecentMessageRecord> recentMessages) {
        if (!StringUtils.hasText(question)
                || session == null
                || recentMessages == null
                || recentMessages.isEmpty()
                || session.getQuestionCount() == null
                || session.getQuestionCount() <= 0) {
            return false;
        }

        String normalized = question.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() <= 12) {
            return true;
        }
        for (String hint : FOLLOW_UP_HINTS) {
            String lowerHint = hint.toLowerCase(Locale.ROOT);
            if (normalized.startsWith(lowerHint) || (normalized.contains(lowerHint) && normalized.length() <= 40)) {
                return true;
            }
        }
        return false;
    }

    private String rewriteQuestionIfNeeded(String question, List<String> historyBlocks) {
        if (historyBlocks == null || historyBlocks.isEmpty()) {
            return question;
        }
        try {
            ModelChatResult result = ollamaService.chat(
                    qaPromptTemplate.buildRewriteSystemPrompt(),
                    qaPromptTemplate.buildRewriteUserPrompt(question, historyBlocks));
            String rewritten = cleanupRewriteResult(result == null ? null : result.getContent());
            return StringUtils.hasText(rewritten) ? rewritten : question;
        } catch (Exception ex) {
            log.warn("Rewrite qa follow-up question failed, fallback to original question", ex);
            return question;
        }
    }

    private String cleanupRewriteResult(String rewritten) {
        if (!StringUtils.hasText(rewritten)) {
            return null;
        }
        String normalized = rewritten.trim();
        normalized = normalized.replace("```", "").trim();
        normalized = normalized.replaceFirst("^(独立问题|重写问题|改写结果)[:：]\\s*", "");
        normalized = normalized.replaceFirst("^\"|\"$", "");
        return normalized.trim();
    }

    private void persistUserQuestionPhase(
            Long teamId,
            Long userId,
            ChatSession session,
            ChatMessage userMessage,
            String question,
            String titleCandidate,
            LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            if (chatMessageMapper.insert(userMessage) != 1) {
                throw new IllegalStateException("Insert user chat message failed");
            }
            int affected = chatSessionMapper.updateSessionAfterUserQuestion(
                    teamId,
                    userId,
                    session.getId(),
                    titleCandidate,
                    abbreviate(question, MAX_PREVIEW_LENGTH),
                    abbreviate(question, MAX_PREVIEW_LENGTH),
                    now);
            if (affected != 1) {
                throw new IllegalStateException("Update chat session after user question failed");
            }
        });
        session.setMessageCount(defaultInt(session.getMessageCount()) + 1);
        session.setQuestionCount(defaultInt(session.getQuestionCount()) + 1);
        session.setLastMessagePreview(abbreviate(question, MAX_PREVIEW_LENGTH));
        session.setLastQuestionSnippet(abbreviate(question, MAX_PREVIEW_LENGTH));
        session.setUpdatedAt(now);
        if (StringUtils.hasText(titleCandidate)) {
            session.setTitle(titleCandidate);
        }
    }

    private ProcessingResult processAssistantAnswer(
            Long teamId,
            Long userId,
            Long sessionId,
            String question,
            String standaloneQuestion,
            List<String> historyBlocks) {
        long startNanos = System.nanoTime();
        try {
            List<Float> vector = arkModelService.createEmbeddings(List.of(standaloneQuestion)).getFirst();
            List<ChunkSearchHit> hits = qdrantVectorService.searchTeamChunks(teamId, vector, RETRIEVAL_TOP_K);
            Map<Long, DocumentChunkMapper.QaChunkRecord> chunkRecordMap = loadChunkRecordMap(teamId, hits);
            List<QaCitationItem> citations = buildCitations(hits, chunkRecordMap);
            List<String> contextBlocks = buildContextBlocks(hits, chunkRecordMap);
            ModelChatResult modelResult = ollamaService.chat(
                    qaPromptTemplate.buildAnswerSystemPrompt(),
                    qaPromptTemplate.buildAnswerUserPrompt(question, contextBlocks, historyBlocks));
            String answer = StringUtils.hasText(modelResult.getContent())
                    ? modelResult.getContent().trim()
                    : SYSTEM_FAILURE_ANSWER;
            String requestStatus = StringUtils.hasText(modelResult.getContent()) ? REQUEST_STATUS_SUCCESS : REQUEST_STATUS_FAILED;
            return new ProcessingResult(
                    answer,
                    requestStatus,
                    safeCitationsJson(citations),
                    citations,
                    hits == null ? 0 : hits.size(),
                    modelResult.getModelName(),
                    modelResult.getPromptTokens(),
                    modelResult.getCompletionTokens(),
                    calculateLatencyMs(startNanos));
        } catch (Exception ex) {
            log.error("Process qa assistant answer failed, teamId={}, userId={}, sessionId={}", teamId, userId, sessionId, ex);
            List<QaCitationItem> citations = List.of();
            return new ProcessingResult(
                    SYSTEM_FAILURE_ANSWER,
                    REQUEST_STATUS_FAILED,
                    safeCitationsJson(citations),
                    citations,
                    0,
                    null,
                    null,
                    null,
                    calculateLatencyMs(startNanos));
        }
    }

    private void persistAssistantPhase(
            Long teamId,
            Long userId,
            ChatSession session,
            RequestLog requestLog,
            ChatMessage assistantMessage,
            ProcessingResult processingResult,
            LocalDateTime now) {
        transactionTemplate.executeWithoutResult(status -> {
            if (requestLogMapper.insert(requestLog) != 1) {
                throw new IllegalStateException("Insert qa request log failed");
            }
            assistantMessage.setRequestLogId(requestLog.getId());
            if (chatMessageMapper.insert(assistantMessage) != 1) {
                throw new IllegalStateException("Insert assistant chat message failed");
            }
            int affected = chatSessionMapper.updateSessionAfterAssistantReply(
                    teamId,
                    userId,
                    session.getId(),
                    abbreviate(processingResult.answer(), MAX_PREVIEW_LENGTH),
                    now);
            if (affected != 1) {
                throw new IllegalStateException("Update chat session after assistant answer failed");
            }
        });
        session.setMessageCount(defaultInt(session.getMessageCount()) + 1);
        session.setLastMessagePreview(abbreviate(processingResult.answer(), MAX_PREVIEW_LENGTH));
        session.setUpdatedAt(now);
    }

    private ChatMessage buildUserMessage(Long sessionId, String question, LocalDateTime now) {
        ChatMessage message = new ChatMessage();
        message.setId(IdWorker.getId());
        message.setSessionId(sessionId);
        message.setRole(MESSAGE_ROLE_USER);
        message.setContent(question);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        return message;
    }

    private ChatMessage buildAssistantMessage(Long sessionId, ProcessingResult processingResult, LocalDateTime now) {
        ChatMessage message = new ChatMessage();
        message.setId(IdWorker.getId());
        message.setSessionId(sessionId);
        message.setRole(MESSAGE_ROLE_ASSISTANT);
        message.setContent(processingResult.answer());
        message.setCitationsJson(processingResult.citationsJson());
        message.setModelName(processingResult.modelName());
        message.setTokenUsagePrompt(processingResult.promptTokens());
        message.setTokenUsageCompletion(processingResult.completionTokens());
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        return message;
    }

    private RequestLog buildRequestLog(
            Long sessionId,
            Long userId,
            String question,
            String standaloneQuestion,
            ProcessingResult processingResult,
            LocalDateTime now) {
        RequestLog requestLog = new RequestLog();
        requestLog.setId(IdWorker.getId());
        requestLog.setSessionId(sessionId);
        requestLog.setUserId(userId);
        requestLog.setModelName(processingResult.modelName());
        requestLog.setStatus(processingResult.status());
        requestLog.setLatencyMs(processingResult.latencyMs());
        requestLog.setQuestion(question);
        requestLog.setQuestionSummary(abbreviate(standaloneQuestion, MAX_PREVIEW_LENGTH));
        requestLog.setAnswer(processingResult.answer());
        requestLog.setHitChunkCount(processingResult.hitChunkCount());
        requestLog.setCitationCount(processingResult.citations().size());
        requestLog.setUsedTool(0);
        requestLog.setCitationsJson(processingResult.citationsJson());
        requestLog.setCreatedAt(now);
        requestLog.setUpdatedAt(now);
        return requestLog;
    }

    private List<String> buildHistoryBlocks(List<ChatMessageMapper.RecentMessageRecord> recentMessages) {
        if (recentMessages == null || recentMessages.isEmpty()) {
            return List.of();
        }
        List<String> historyBlocks = new ArrayList<>(recentMessages.size());
        for (ChatMessageMapper.RecentMessageRecord message : recentMessages) {
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            String speaker = MESSAGE_ROLE_ASSISTANT.equalsIgnoreCase(message.getRole()) ? "assistant" : "user";
            historyBlocks.add(speaker + ": " + abbreviate(message.getContent().trim(), MAX_CONTEXT_LENGTH));
        }
        return historyBlocks;
    }

    private Map<Long, DocumentChunkMapper.QaChunkRecord> loadChunkRecordMap(Long teamId, List<ChunkSearchHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return Map.of();
        }
        List<Long> chunkIds = hits.stream()
                .map(ChunkSearchHit::getChunkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (chunkIds.isEmpty()) {
            return Map.of();
        }
        List<DocumentChunkMapper.QaChunkRecord> records = documentChunkMapper.selectQaChunkRecords(teamId, chunkIds);
        if (records == null || records.isEmpty()) {
            return Map.of();
        }
        Map<Long, DocumentChunkMapper.QaChunkRecord> recordMap = new LinkedHashMap<>(records.size());
        for (DocumentChunkMapper.QaChunkRecord record : records) {
            if (record != null && record.getChunkId() != null) {
                recordMap.put(record.getChunkId(), record);
            }
        }
        return recordMap;
    }

    private List<String> buildContextBlocks(
            List<ChunkSearchHit> hits,
            Map<Long, DocumentChunkMapper.QaChunkRecord> chunkRecordMap) {
        if (hits == null || hits.isEmpty() || chunkRecordMap == null || chunkRecordMap.isEmpty()) {
            return List.of();
        }

        List<String> contextBlocks = new ArrayList<>(hits.size());
        for (ChunkSearchHit hit : hits) {
            DocumentChunkMapper.QaChunkRecord record = chunkRecordMap.get(hit.getChunkId());
            if (record == null) {
                continue;
            }
            String evidenceText = StringUtils.hasText(record.getContent()) ? record.getContent().trim() : record.getSummary();
            if (!StringUtils.hasText(evidenceText)) {
                continue;
            }
            StringBuilder block = new StringBuilder();
            block.append("documentName: ").append(defaultString(record.getDocumentName(), "unknown")).append(System.lineSeparator());
            block.append("documentId: ").append(record.getDocumentId()).append(System.lineSeparator());
            block.append("chunkIndex: ").append(defaultInt(record.getChunkIndex())).append(System.lineSeparator());
            if (record.getPageNo() != null) {
                block.append("pageNo: ").append(record.getPageNo()).append(System.lineSeparator());
            }
            if (StringUtils.hasText(record.getSectionTitle())) {
                block.append("sectionTitle: ").append(record.getSectionTitle().trim()).append(System.lineSeparator());
            }
            block.append("content: ").append(abbreviate(evidenceText, MAX_CONTEXT_LENGTH));
            contextBlocks.add(block.toString());
        }
        return contextBlocks;
    }

    private List<QaCitationItem> buildCitations(
            List<ChunkSearchHit> hits,
            Map<Long, DocumentChunkMapper.QaChunkRecord> chunkRecordMap) {
        if (hits == null || hits.isEmpty() || chunkRecordMap == null || chunkRecordMap.isEmpty()) {
            return List.of();
        }

        List<QaCitationItem> citations = new ArrayList<>(hits.size());
        int index = 1;
        for (ChunkSearchHit hit : hits) {
            DocumentChunkMapper.QaChunkRecord record = chunkRecordMap.get(hit.getChunkId());
            if (record == null) {
                continue;
            }
            QaCitationItem citation = new QaCitationItem();
            citation.setCitationId("citation_" + index);
            citation.setDocumentId(record.getDocumentId() == null ? null : String.valueOf(record.getDocumentId()));
            citation.setDocumentName(record.getDocumentName());
            citation.setChunkIndex(record.getChunkIndex());
            String snippetSource = StringUtils.hasText(record.getSummary()) ? record.getSummary() : record.getContent();
            citation.setSnippet(abbreviate(snippetSource, MAX_CITATION_SNIPPET_LENGTH));
            citations.add(citation);
            index++;
        }
        return citations;
    }

    private String safeCitationsJson(List<QaCitationItem> citations) {
        try {
            return objectMapper.writeValueAsString(citations == null ? List.of() : citations);
        } catch (Exception ex) {
            log.warn("Serialize qa citations failed", ex);
            return "[]";
        }
    }

    private int calculateLatencyMs(long startNanos) {
        long elapsedNanos = System.nanoTime() - startNanos;
        long elapsedMs = elapsedNanos / 1_000_000L;
        return elapsedMs > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsedMs;
    }

    private boolean shouldUseQuestionAsTitle(ChatSession session) {
        if (session == null) {
            return false;
        }
        return defaultInt(session.getQuestionCount()) <= 0
                || !StringUtils.hasText(session.getTitle())
                || DEFAULT_SESSION_TITLE.equals(session.getTitle());
    }

    private QaMessageItem buildMessageItem(ChatMessage message, List<QaCitationItem> citations) {
        QaMessageItem item = new QaMessageItem();
        item.setMessageId(message.getId() == null ? null : String.valueOf(message.getId()));
        item.setRole(message.getRole());
        item.setContent(message.getContent());
        item.setCreatedAt(formatDateTime(message.getCreatedAt()));
        item.setCitations(citations == null ? List.of() : citations);
        return item;
    }

    private QaSessionItem buildSessionItem(ChatSession session) {
        QaSessionItem item = new QaSessionItem();
        item.setSessionId(session.getId() == null ? null : String.valueOf(session.getId()));
        item.setTitle(StringUtils.hasText(session.getTitle()) ? session.getTitle() : DEFAULT_SESSION_TITLE);
        item.setCreatedAt(formatDateTime(session.getCreatedAt()));
        item.setUpdatedAt(formatDateTime(session.getUpdatedAt()));
        item.setLastMessagePreview(defaultString(session.getLastMessagePreview(), ""));
        item.setMessageCount(defaultInt(session.getMessageCount()));
        return item;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String defaultString(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.trim().replaceAll("\\s+", " ");
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private record ProcessingResult(
            String answer,
            String status,
            String citationsJson,
            List<QaCitationItem> citations,
            Integer hitChunkCount,
            String modelName,
            Integer promptTokens,
            Integer completionTokens,
            Integer latencyMs) {
    }
}
