package com.wwt.assistant.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.UserContextHolder;
import com.wwt.assistant.dto.document.request.BatchDeleteDocumentsRequest;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.request.UploadDocumentRequest;
import com.wwt.assistant.dto.document.response.DocumentChunkItem;
import com.wwt.assistant.dto.document.response.DocumentDetailResponse;
import com.wwt.assistant.dto.document.response.DocumentListItem;
import com.wwt.assistant.dto.document.response.DocumentPageResponse;
import com.wwt.assistant.dto.document.response.OptionItem;
import com.wwt.assistant.dto.vector.ChunkVectorUpsertRequest;
import com.wwt.assistant.entity.KbDocument;
import com.wwt.assistant.entity.KbDocumentChunk;
import com.wwt.assistant.entity.KbKnowledgeBase;
import com.wwt.assistant.entity.SysUser;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import com.wwt.assistant.mapper.DocumentMapper;
import com.wwt.assistant.mapper.SysUserMapper;
import com.wwt.assistant.service.ArkModelService;
import com.wwt.assistant.service.DocumentService;
import com.wwt.assistant.service.QdrantVectorService;
import com.wwt.assistant.utils.DocumentChunkingUtil;
import com.wwt.assistant.utils.MinioUtil;
import com.wwt.assistant.utils.TextCleanerUtil;
import com.wwt.assistant.utils.parser.DocumentParser;
import com.wwt.assistant.utils.parser.DocumentParserFactory;
import com.wwt.assistant.utils.parser.ParseResult;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final String STATUS_UNPARSED = "unparsed";
    private static final String STATUS_PARSING = "parsing";
    private static final String STATUS_CHUNKED = "chunked";
    private static final String STATUS_VECTORIZING = "vectorizing";
    private static final String STATUS_DELETING = "deleting";
    private static final String STATUS_COMPLETED = "completed";
    private static final String VECTOR_STATUS_PENDING = "pending";
    private static final String PARSER_VERSION = "v1";
    private static final int MAX_PARSE_ERROR_MESSAGE_LENGTH = 1000;
    private static final Set<String> VALID_DOCUMENT_STATUSES =
            Set.of("unparsed", "parsing", "chunked", "vectorizing", "deleting", "completed", "failed");
    private static final List<String> PROCESSABLE_START_STATUSES = List.of(STATUS_UNPARSED, "failed");
    private static final List<String> DELETABLE_DOCUMENT_STATUSES =
            List.of(STATUS_UNPARSED, STATUS_PARSING, STATUS_CHUNKED, STATUS_VECTORIZING, STATUS_COMPLETED, "failed");
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("pdf", "docx", "md", "txt");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final SysUserMapper sysUserMapper;
    private final MinioUtil minioUtil;
    private final ArkModelService arkModelService;
    private final QdrantVectorService qdrantVectorService;
    private final DocumentParserFactory documentParserFactory;
    private final ApplicationContext applicationContext;

    @Override
    public ApiResponse<DocumentPageResponse> getDocuments(DocumentQuery query) {
        Long teamId = UserContextHolder.getTeamId();
        if (teamId == null) {
            return ApiResponse.fail(403, "当前用户未关联团队");
        }

        DocumentQuery normalizedQuery = normalizeQuery(query);
        if (normalizedQuery.getPage() <= 0 || normalizedQuery.getPageSize() <= 0) {
            return ApiResponse.fail(400, "分页参数必须大于 0");
        }
        if (StringUtils.hasText(normalizedQuery.getStatus())
                && !VALID_DOCUMENT_STATUSES.contains(normalizedQuery.getStatus())) {
            return ApiResponse.fail(400, "文档状态参数不合法");
        }

        long page = normalizedQuery.getPage();
        long pageSize = normalizedQuery.getPageSize();

        Page<DocumentListItem> pageRequest = Page.of(page, pageSize);
        IPage<DocumentListItem> documentPage = documentMapper.selectDocumentPage(pageRequest, teamId, normalizedQuery);
        List<DocumentListItem> list = documentPage.getRecords();
        List<OptionItem> knowledgeBaseOptions = documentMapper.selectKnowledgeBaseOptions(teamId);

        DocumentPageResponse response = new DocumentPageResponse();
        response.setList(list);
        response.setTotal(documentPage.getTotal());
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setKnowledgeBaseOptions(knowledgeBaseOptions);
        return ApiResponse.success(response);
    }

    @Override
    public ApiResponse<DocumentDetailResponse> getDocument(String documentId) {
        Long teamId = UserContextHolder.getTeamId();
        if (teamId == null) {
            return ApiResponse.fail(403, "当前用户未关联团队");
        }
        if (!StringUtils.hasText(documentId)) {
            return ApiResponse.fail(400, "文档ID不能为空");
        }

        Long documentIdValue;
        try {
            documentIdValue = parseId(documentId, "文档ID参数不合法");
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(400, ex.getMessage());
        }

        DocumentDetailResponse detail = documentMapper.selectDocumentDetail(teamId, documentIdValue);
        if (detail == null) {
            return ApiResponse.fail(404, "文档不存在");
        }

        Long knowledgeBaseId = parseId(detail.getKnowledgeBaseId(), "文档知识库数据不合法");
        List<DocumentChunkItem> chunks = documentMapper.selectDocumentChunks(knowledgeBaseId, documentIdValue);
        detail.setChunks(chunks);
        return ApiResponse.success(detail);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<DocumentDetailResponse> createDocument(UploadDocumentRequest request) {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }

        if (request == null || !StringUtils.hasText(request.getKnowledgeBaseId())) {
            return ApiResponse.fail(400, "知识库不能为空");
        }

        MultipartFile file = request.getFile();
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail(400, "上传文件不能为空");
        }

        String fileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename().trim() : null;
        if (!StringUtils.hasText(fileName)) {
            return ApiResponse.fail(400, "上传文件名不能为空");
        }

        String fileType = getFileType(fileName);
        if (!SUPPORTED_FILE_TYPES.contains(fileType)) {
            return ApiResponse.fail(400, "文件类型不支持");
        }

        Long knowledgeBaseId;
        try {
            knowledgeBaseId = parseId(request.getKnowledgeBaseId(), "知识库参数不合法");
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(400, ex.getMessage());
        }
        KbKnowledgeBase knowledgeBase = documentMapper.selectActiveKnowledgeBase(teamId, knowledgeBaseId);
        if (knowledgeBase == null) {
            return ApiResponse.fail(400, "知识库不存在");
        }

        SysUser currentUser = sysUserMapper.selectById(userId);
        String uploader = resolveUploader(currentUser);
        String objectName = null;
        try {
            objectName = minioUtil.upload(file);

            LocalDateTime now = LocalDateTime.now();
            KbDocument document = buildDocumentEntity(request, knowledgeBaseId, userId, fileName, fileType, objectName, now);
            if (documentMapper.insert(document) != 1) {
                throw new IllegalStateException("保存文档记录失败");
            }
            if (documentMapper.increaseDocumentCount(teamId, knowledgeBaseId) != 1) {
                throw new IllegalStateException("更新知识库文档数失败");
            }

            registerDocumentProcessingAfterCommit(teamId, document.getId());
            return ApiResponse.success(buildDocumentDetailResponse(document, knowledgeBase.getName(), uploader, List.of()));
        } catch (IllegalArgumentException ex) {
            cleanupUploadedObject(objectName);
            return ApiResponse.fail(413, "上传文件大小超过限制");
        } catch (Exception ex) {
            cleanupUploadedObject(objectName);
            return ApiResponse.fail(500, "上传文档失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> deleteDocument(String documentId) {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }
        if (!StringUtils.hasText(documentId)) {
            return ApiResponse.fail(400, "文档ID不能为空");
        }

        Long documentIdValue;
        try {
            documentIdValue = parseId(documentId, "文档ID参数不合法");
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(400, ex.getMessage());
        }

        try {
            int deletedCount = deleteDocuments(teamId, userId, List.of(documentIdValue));
            if (deletedCount == 0) {
                return ApiResponse.fail(404, "文档不存在");
            }
            return ApiResponse.success(null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(409, ex.getMessage());
        } catch (Exception ex) {
            log.error("Delete document failed, teamId={}, userId={}, documentId={}", teamId, userId, documentIdValue, ex);
            return ApiResponse.fail(500, "删除文档失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Void> batchDeleteDocuments(BatchDeleteDocumentsRequest request) {
        Long userId = UserContextHolder.getUserId();
        Long teamId = UserContextHolder.getTeamId();
        if (userId == null || teamId == null) {
            return ApiResponse.fail(403, "当前用户登录状态无效");
        }

        List<Long> documentIds;
        try {
            documentIds = parseDocumentIds(request == null ? null : request.getDocumentIds());
        } catch (IllegalArgumentException ex) {
            return ApiResponse.fail(400, ex.getMessage());
        }
        if (documentIds.isEmpty()) {
            return ApiResponse.fail(400, "documentIds 不能为空");
        }

        try {
            int deletedCount = deleteDocuments(teamId, userId, documentIds);
            if (deletedCount == 0) {
                return ApiResponse.fail(404, "文档不存在");
            }
            return ApiResponse.success(null);
        } catch (IllegalStateException ex) {
            return ApiResponse.fail(409, ex.getMessage());
        } catch (Exception ex) {
            log.error("Batch delete documents failed, teamId={}, userId={}, documentIds={}", teamId, userId, documentIds, ex);
            return ApiResponse.fail(500, "批量删除文档失败");
        }
    }

    @Override
    @Async("documentProcessExecutor")
    public void processDocumentAsync(Long teamId, Long documentId) {
        processDocument(teamId, documentId);
    }

    @Override
    public void processDocument(Long teamId, Long documentId) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }

        KbDocument document = documentMapper.selectDocumentForProcessing(teamId, documentId);
        if (document == null) {
            log.warn(
                    "Skip document processing because document does not exist or does not belong to team, teamId={}, documentId={}",
                    teamId,
                    documentId);
            return;
        }

        try {
            validateProcessableDocument(document);
            if (!tryAcquireProcessingRight(documentId)) {
                log.info(
                        "Skip document processing because processing right was not acquired, teamId={}, documentId={}",
                        teamId,
                        documentId);
                return;
            }
            DocumentParser parser = documentParserFactory.getParser(document.getFileType());

            ParseResult parseResult;
            try (InputStream inputStream = minioUtil.getObject(document.getFileUrl())) {
                parseResult = parser.parse(document.getName(), inputStream);
            }

            if (parseResult == null || !parseResult.isSuccess()) {
                String errorMessage = parseResult == null ? "document parser returned null result" : parseResult.getErrorMessage();
                markDocumentProcessingFailed(documentId, errorMessage);
                log.warn("Document parsing failed, teamId={}, documentId={}, error={}", teamId, documentId, errorMessage);
                return;
            }

            ParseResult cleanedResult = TextCleanerUtil.clean(parseResult);
            chunkAndSaveDocument(teamId, documentId, cleanedResult);
            log.info("Document processing completed, teamId={}, documentId={}", teamId, documentId);
        } catch (Exception ex) {
            markDocumentProcessingFailed(documentId, ex.getMessage());
            log.error("Document processing failed, teamId={}, documentId={}", teamId, documentId, ex);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int chunkAndSaveDocument(Long teamId, Long documentId, ParseResult parseResult) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        if (documentId == null) {
            throw new IllegalArgumentException("documentId must not be null");
        }
        if (parseResult == null) {
            throw new IllegalArgumentException("parseResult must not be null");
        }

        KbDocument document = documentMapper.selectDocumentForProcessing(teamId, documentId);
        if (document == null || document.getKnowledgeBaseId() == null) {
            throw new IllegalArgumentException("document does not exist");
        }

        List<DocumentChunkingUtil.TextChunk> generatedChunks = DocumentChunkingUtil.split(parseResult);
        List<KbDocumentChunk> chunkEntities = buildChunkEntities(document, generatedChunks);
        if (chunkEntities.isEmpty()) {
            throw new IllegalStateException("no valid chunks generated for document");
        }

        int generatedCount = chunkEntities.size();
        int insertedCount = 0;
        log.info("Start chunk persistence, documentId={}, generatedChunks={}", documentId, generatedCount);

        try {
            documentChunkMapper.deleteByDocumentId(documentId);
            insertedCount = documentChunkMapper.batchInsert(chunkEntities);
            if (insertedCount != generatedCount) {
                throw new IllegalStateException("inserted chunk count does not match generated chunk count");
            }

            LocalDateTime now = LocalDateTime.now();
            int updated = documentMapper.updateChunkingResult(
                    documentId,
                    generatedCount,
                    STATUS_CHUNKED,
                    null,
                    now,
                    now,
                    now);
            if (updated != 1) {
                throw new IllegalStateException("failed to update document chunking result");
            }

            registerVectorizationAfterCommit(teamId, chunkEntities);
            log.info(
                    "Chunk persistence completed, documentId={}, generatedChunks={}, insertedChunks={}, status={}",
                    documentId,
                    generatedCount,
                    insertedCount,
                    STATUS_CHUNKED);
            return generatedCount;
        } catch (RuntimeException ex) {
            log.error(
                    "Chunk persistence failed, documentId={}, generatedChunks={}, insertedChunks={}, status={}",
                    documentId,
                    generatedCount,
                    insertedCount,
                    "failed",
                    ex);
            throw ex;
        }
    }

    @Override
    @Async("documentVectorExecutor")
    public void embedAndUpsertDocumentChunks(Long teamId, List<KbDocumentChunk> chunks) {
        if (teamId == null) {
            throw new IllegalArgumentException("teamId must not be null");
        }
        if (chunks == null || chunks.isEmpty()) {
            log.warn("Skip chunk vectorization because chunks are empty, teamId={}", teamId);
            return;
        }

        List<KbDocumentChunk> validChunks = chunks.stream()
                .filter(Objects::nonNull)
                .filter(chunk -> chunk.getId() != null && StringUtils.hasText(chunk.getContent()))
                .toList();
        if (validChunks.isEmpty()) {
            log.warn("Skip chunk vectorization because no valid chunk content exists, teamId={}", teamId);
            return;
        }

        List<Long> chunkIds = validChunks.stream().map(KbDocumentChunk::getId).toList();
        try {
            // Step 1: Validate tenant ownership using backend data, not frontend parameters.
            KbDocument document = resolveVectorizationDocument(teamId, validChunks);
            markDocumentVectorizing(document.getId());

            // Step 2: Generate embeddings for each chunk content through Volcengine Ark.
            List<String> texts = validChunks.stream()
                    .map(KbDocumentChunk::getContent)
                    .map(String::trim)
                    .toList();
            List<List<Float>> vectors = arkModelService.createEmbeddings(texts);

            // Step 3: Convert MySQL chunks into Qdrant point requests with tenant metadata payload.
            List<ChunkVectorUpsertRequest> requests = buildChunkVectorRequests(teamId, document, validChunks, vectors);

            // Step 4: Upsert vectors into the single shared Qdrant collection.
            qdrantVectorService.upsertChunks(requests);

            // Step 5: Mark MySQL chunks as vectorized after Qdrant write succeeds.
            documentChunkMapper.markVectorCompleted(chunkIds);
            markDocumentCompleted(document.getId());
            log.info(
                    "Chunk vectorization completed, teamId={}, documentId={}, chunkCount={}",
                    teamId,
                    document.getId(),
                    validChunks.size());
        } catch (Exception ex) {
            markVectorFailedQuietly(chunkIds);
            markDocumentProcessingFailed(resolveDocumentId(validChunks), ex.getMessage());
            log.error("Chunk vectorization failed, teamId={}, chunkIds={}", teamId, chunkIds, ex);
            throw ex;
        }
    }

    @Override
    @Async("documentProcessExecutor")
    public void cleanupDeletedDocumentResourcesAsync(Long teamId, Long documentId, String fileUrl) {
        RuntimeException cleanupException = null;

        try {
            qdrantVectorService.deleteChunksByDocument(teamId, documentId);
        } catch (RuntimeException ex) {
            cleanupException = ex;
            log.error("Failed to delete document vectors from Qdrant, teamId={}, documentId={}", teamId, documentId, ex);
        }

        if (StringUtils.hasText(fileUrl)) {
            try {
                minioUtil.remove(fileUrl);
            } catch (RuntimeException ex) {
                if (cleanupException == null) {
                    cleanupException = ex;
                }
                log.error(
                        "Failed to delete document file from MinIO, teamId={}, documentId={}, fileUrl={}",
                        teamId,
                        documentId,
                        fileUrl,
                        ex);
            }
        }

        if (cleanupException != null) {
            throw cleanupException;
        }
    }

    private DocumentQuery normalizeQuery(DocumentQuery query) {
        DocumentQuery normalized = new DocumentQuery();
        if (query == null) {
            normalized.setPage(DEFAULT_PAGE);
            normalized.setPageSize(DEFAULT_PAGE_SIZE);
            return normalized;
        }

        normalized.setKeyword(normalizeText(query.getKeyword()));
        normalized.setStatus(normalizeText(query.getStatus()));
        normalized.setUploader(normalizeText(query.getUploader()));
        normalized.setPage(query.getPage() == null ? DEFAULT_PAGE : query.getPage());
        normalized.setPageSize(query.getPageSize() == null ? DEFAULT_PAGE_SIZE : query.getPageSize());
        return normalized;
    }

    private KbDocument buildDocumentEntity(
            UploadDocumentRequest request,
            Long knowledgeBaseId,
            Long userId,
            String fileName,
            String fileType,
            String objectName,
            LocalDateTime now) {
        KbDocument document = new KbDocument();
        document.setId(IdWorker.getId());
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setName(fileName);
        document.setFileType(fileType);
        document.setFileUrl(objectName);
        document.setFileSize(request.getFile().getSize());
        document.setStatus(STATUS_UNPARSED);
        document.setChunkCount(0);
        document.setRemark(normalizeText(request.getRemark()));
        document.setCreatedBy(userId);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }

    private List<KbDocumentChunk> buildChunkEntities(KbDocument document, List<DocumentChunkingUtil.TextChunk> generatedChunks) {
        LocalDateTime now = LocalDateTime.now();
        List<KbDocumentChunk> chunkEntities = new ArrayList<>();
        for (DocumentChunkingUtil.TextChunk generatedChunk : generatedChunks) {
            String content = generatedChunk.content() == null ? null : generatedChunk.content().trim();
            if (!StringUtils.hasText(content)) {
                continue;
            }

            KbDocumentChunk chunk = new KbDocumentChunk();
            chunk.setId(IdWorker.getId());
            chunk.setDocumentId(document.getId());
            chunk.setKnowledgeBaseId(document.getKnowledgeBaseId());
            chunk.setChunkIndex(chunkEntities.size() + 1);
            chunk.setContent(content);
            chunk.setTokenCount(1);
            chunk.setVectorStatus(VECTOR_STATUS_PENDING);
            chunk.setPageNo(generatedChunk.pageNo());
            chunk.setSectionTitle(normalizeText(generatedChunk.sectionTitle()));
            chunk.setCharCount(content.length());
            chunk.setCreatedAt(now);
            chunk.setUpdatedAt(now);
            chunkEntities.add(chunk);
        }
        return chunkEntities;
    }

    private void registerDocumentProcessingAfterCommit(Long teamId, Long documentId) {
        if (teamId == null || documentId == null) {
            log.warn(
                    "Skip async document processing because teamId or documentId is missing, teamId={}, documentId={}",
                    teamId,
                    documentId);
            return;
        }

        Runnable task = () -> applicationContext
                .getBean(DocumentService.class)
                .processDocumentAsync(teamId, documentId);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }

        task.run();
    }

    private void registerVectorizationAfterCommit(Long teamId, List<KbDocumentChunk> chunkEntities) {
        if (teamId == null) {
            log.warn("Skip async vectorization because current user teamId is missing");
            return;
        }
        if (chunkEntities == null || chunkEntities.isEmpty()) {
            return;
        }

        List<KbDocumentChunk> scheduledChunks = Collections.unmodifiableList(new ArrayList<>(chunkEntities));
        Runnable task = () -> applicationContext
                .getBean(DocumentService.class)
                .embedAndUpsertDocumentChunks(teamId, scheduledChunks);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }

        task.run();
    }

    private void registerDocumentResourceCleanupAfterCommit(Long teamId, KbDocument document) {
        if (teamId == null || document == null || document.getId() == null) {
            log.warn("Skip async document cleanup because teamId or document is missing, teamId={}, document={}", teamId, document);
            return;
        }

        Long documentId = document.getId();
        String fileUrl = document.getFileUrl();
        Runnable task = () -> applicationContext
                .getBean(DocumentService.class)
                .cleanupDeletedDocumentResourcesAsync(teamId, documentId, fileUrl);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
            return;
        }

        task.run();
    }

    private void validateProcessableDocument(KbDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        if (STATUS_DELETING.equals(document.getStatus())) {
            throw new IllegalStateException("document is deleting");
        }
        if (!StringUtils.hasText(document.getName())) {
            throw new IllegalStateException("document name must not be blank");
        }
        if (!StringUtils.hasText(document.getFileType())) {
            throw new IllegalStateException("document fileType must not be blank");
        }
        if (!StringUtils.hasText(document.getFileUrl())) {
            throw new IllegalStateException("document fileUrl must not be blank");
        }
    }

    private boolean tryAcquireProcessingRight(Long documentId) {
        LocalDateTime now = LocalDateTime.now();
        int updated = documentMapper.tryMarkParsingStarted(
                documentId,
                PROCESSABLE_START_STATUSES,
                PARSER_VERSION,
                now,
                now);
        return updated == 1;
    }

    private int deleteDocuments(Long teamId, Long userId, List<Long> rawDocumentIds) {
        List<Long> documentIds = deduplicateDocumentIds(rawDocumentIds);
        if (documentIds.isEmpty()) {
            return 0;
        }

        List<KbDocument> documents = documentMapper.selectDocumentsForDeletion(teamId, documentIds);
        if (documents.isEmpty()) {
            return 0;
        }

        List<Long> matchedDocumentIds = documents.stream()
                .map(KbDocument::getId)
                .filter(Objects::nonNull)
                .toList();
        LocalDateTime now = LocalDateTime.now();

        int markedCount = documentMapper.markDocumentsDeleting(matchedDocumentIds, DELETABLE_DOCUMENT_STATUSES, now);
        if (markedCount != matchedDocumentIds.size()) {
            throw new IllegalStateException("部分文档当前无法删除，请稍后重试");
        }

        documentChunkMapper.softDeleteByDocumentIds(matchedDocumentIds, now, userId, now);

        int deletedDocumentCount = documentMapper.softDeleteDocuments(matchedDocumentIds, now, userId, now);
        if (deletedDocumentCount != matchedDocumentIds.size()) {
            throw new IllegalStateException("软删除文档失败");
        }

        for (Map.Entry<Long, Integer> entry : countDocumentsByKnowledgeBase(documents).entrySet()) {
            int affected = documentMapper.decreaseDocumentCount(teamId, entry.getKey(), entry.getValue());
            if (affected != 1) {
                throw new IllegalStateException("更新知识库文档数失败");
            }
        }

        documents.forEach(document -> registerDocumentResourceCleanupAfterCommit(teamId, document));
        return matchedDocumentIds.size();
    }

    private Map<Long, Integer> countDocumentsByKnowledgeBase(List<KbDocument> documents) {
        Map<Long, Integer> countMap = new LinkedHashMap<>();
        for (KbDocument document : documents) {
            if (document == null || document.getKnowledgeBaseId() == null) {
                continue;
            }
            countMap.merge(document.getKnowledgeBaseId(), 1, Integer::sum);
        }
        return countMap;
    }

    private List<Long> parseDocumentIds(Collection<String> rawDocumentIds) {
        if (rawDocumentIds == null || rawDocumentIds.isEmpty()) {
            return List.of();
        }

        List<Long> documentIds = new ArrayList<>();
        for (String rawDocumentId : rawDocumentIds) {
            if (!StringUtils.hasText(rawDocumentId)) {
                continue;
            }
            documentIds.add(parseId(rawDocumentId, "documentIds 参数不合法"));
        }
        return deduplicateDocumentIds(documentIds);
    }

    private List<Long> deduplicateDocumentIds(Collection<Long> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return List.of();
        }

        List<Long> normalizedIds = new ArrayList<>();
        for (Long documentId : documentIds) {
            if (documentId == null || normalizedIds.contains(documentId)) {
                continue;
            }
            normalizedIds.add(documentId);
        }
        return normalizedIds;
    }

    private void markDocumentProcessingFailed(Long documentId, String errorMessage) {
        if (documentId == null) {
            log.error("Skip marking document processing as failed because documentId is null, error={}", errorMessage);
            return;
        }
        try {
            LocalDateTime now = LocalDateTime.now();
            documentMapper.markProcessingFailed(documentId, truncateErrorMessage(errorMessage), now, now);
        } catch (Exception ex) {
            log.error("Failed to mark document processing as failed, documentId={}", documentId, ex);
        }
    }

    private void markDocumentVectorizing(Long documentId) {
        updateDocumentStatus(documentId, STATUS_VECTORIZING, "vectorizing");
    }

    private void markDocumentCompleted(Long documentId) {
        updateDocumentStatus(documentId, STATUS_COMPLETED, "completed");
    }

    private void updateDocumentStatus(Long documentId, String status, String operation) {
        LocalDateTime now = LocalDateTime.now();
        int updated = documentMapper.updateDocumentStatus(documentId, status, now);
        if (updated != 1) {
            throw new IllegalStateException("failed to mark document " + operation);
        }
    }

    private Long resolveDocumentId(List<KbDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return null;
        }
        return chunks.get(0).getDocumentId();
    }

    private KbDocument resolveVectorizationDocument(Long teamId, List<KbDocumentChunk> chunks) {
        KbDocumentChunk firstChunk = chunks.get(0);
        Long documentId = firstChunk.getDocumentId();
        Long knowledgeBaseId = firstChunk.getKnowledgeBaseId();
        if (documentId == null || knowledgeBaseId == null) {
            throw new IllegalArgumentException("chunk documentId and knowledgeBaseId must not be null");
        }
        boolean allSameDocument = chunks.stream().allMatch(chunk ->
                Objects.equals(documentId, chunk.getDocumentId())
                        && Objects.equals(knowledgeBaseId, chunk.getKnowledgeBaseId()));
        if (!allSameDocument) {
            throw new IllegalArgumentException("chunks must belong to the same document");
        }

        KbDocument document = documentMapper.selectVectorizationDocument(teamId, documentId, knowledgeBaseId);
        if (document == null) {
            throw new IllegalArgumentException("document does not belong to current team");
        }
        if (STATUS_DELETING.equals(document.getStatus())) {
            throw new IllegalArgumentException("document is deleting");
        }
        return document;
    }

    private List<ChunkVectorUpsertRequest> buildChunkVectorRequests(
            Long teamId,
            KbDocument document,
            List<KbDocumentChunk> chunks,
            List<List<Float>> vectors) {
        if (vectors.size() != chunks.size()) {
            throw new IllegalStateException("embedding vector count does not match chunk count");
        }

        List<ChunkVectorUpsertRequest> requests = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            KbDocumentChunk chunk = chunks.get(i);
            requests.add(ChunkVectorUpsertRequest.builder()
                    .teamId(teamId)
                    .knowledgeBaseId(chunk.getKnowledgeBaseId())
                    .documentId(chunk.getDocumentId())
                    .chunkId(chunk.getId())
                    .chunkIndex(chunk.getChunkIndex())
                    .vector(vectors.get(i))
                    .fileType(document.getFileType())
                    .status(STATUS_COMPLETED)
                    .build());
        }
        return requests;
    }

    private void markVectorFailedQuietly(List<Long> chunkIds) {
        try {
            documentChunkMapper.markVectorFailed(chunkIds);
        } catch (Exception statusEx) {
            log.error("Failed to mark chunk vector status as failed, chunkIds={}", chunkIds, statusEx);
        }
    }

    private DocumentDetailResponse buildDocumentDetailResponse(
            KbDocument document,
            String knowledgeBaseName,
            String uploader,
            List<DocumentChunkItem> chunks) {
        DocumentDetailResponse response = new DocumentDetailResponse();
        response.setDocumentId(String.valueOf(document.getId()));
        response.setDocumentName(document.getName());
        response.setKnowledgeBaseId(String.valueOf(document.getKnowledgeBaseId()));
        response.setKnowledgeBaseName(knowledgeBaseName);
        response.setFileType(document.getFileType());
        response.setStatus(document.getStatus());
        response.setChunkCount(document.getChunkCount());
        response.setUploader(uploader);
        response.setUploadedAt(formatDateTime(document.getUploadedAt()));
        response.setRemark(document.getRemark());
        response.setChunks(chunks);
        return response;
    }

    private String resolveUploader(SysUser currentUser) {
        if (currentUser != null) {
            if (StringUtils.hasText(currentUser.getName())) {
                return currentUser.getName().trim();
            }
            if (StringUtils.hasText(currentUser.getUsername())) {
                return currentUser.getUsername().trim();
            }
        }
        return UserContextHolder.getUsername();
    }

    private Long parseId(String rawId, String message) {
        try {
            return Long.valueOf(rawId.trim());
        } catch (Exception ex) {
            throw new IllegalArgumentException(message, ex);
        }
    }

    private String getFileType(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String formatDateTime(LocalDateTime value) {
        return value == null ? null : value.format(DATETIME_FORMATTER);
    }

    private String truncateErrorMessage(String errorMessage) {
        String normalized = normalizeText(errorMessage);
        if (!StringUtils.hasText(normalized)) {
            return "document processing failed";
        }
        return normalized.length() <= MAX_PARSE_ERROR_MESSAGE_LENGTH
                ? normalized
                : normalized.substring(0, MAX_PARSE_ERROR_MESSAGE_LENGTH);
    }

    private void cleanupUploadedObject(String objectName) {
        if (!StringUtils.hasText(objectName)) {
            return;
        }
        try {
            minioUtil.remove(objectName);
        } catch (Exception cleanupEx) {
            // MinIO 清理失败不覆盖主异常，只记录影响范围。
        }
    }
}
