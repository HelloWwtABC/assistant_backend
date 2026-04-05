package com.wwt.assistant.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.UserContextHolder;
import com.wwt.assistant.dto.document.request.DocumentQuery;
import com.wwt.assistant.dto.document.request.UploadDocumentRequest;
import com.wwt.assistant.dto.document.response.DocumentDetailResponse;
import com.wwt.assistant.dto.document.response.DocumentListItem;
import com.wwt.assistant.dto.document.response.DocumentPageResponse;
import com.wwt.assistant.dto.document.response.OptionItem;
import com.wwt.assistant.entity.KbDocument;
import com.wwt.assistant.entity.KbKnowledgeBase;
import com.wwt.assistant.entity.SysUser;
import com.wwt.assistant.mapper.DocumentMapper;
import com.wwt.assistant.mapper.SysUserMapper;
import com.wwt.assistant.service.DocumentService;
import com.wwt.assistant.utils.MinioUtil;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private static final long DEFAULT_PAGE = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final String STATUS_PARSING = "parsing";
    private static final Set<String> VALID_DOCUMENT_STATUSES = Set.of("unparsed", "parsing", "completed", "failed");
    private static final Set<String> SUPPORTED_FILE_TYPES = Set.of("pdf", "docx", "md", "txt");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DocumentMapper documentMapper;
    private final SysUserMapper sysUserMapper;
    private final MinioUtil minioUtil;

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

            return ApiResponse.success(buildDocumentDetailResponse(document, knowledgeBase.getName(), uploader));
        } catch (IllegalArgumentException ex) {
            cleanupUploadedObject(objectName);
            return ApiResponse.fail(413, "上传文件大小超过限制");
        } catch (Exception ex) {
            cleanupUploadedObject(objectName);
            return ApiResponse.fail(500, "上传文档失败");
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
        document.setStatus(STATUS_PARSING);
        document.setChunkCount(0);
        document.setRemark(normalizeText(request.getRemark()));
        document.setCreatedBy(userId);
        document.setUploadedAt(now);
        document.setCreatedAt(now);
        document.setUpdatedAt(now);
        return document;
    }

    private DocumentDetailResponse buildDocumentDetailResponse(KbDocument document, String knowledgeBaseName, String uploader) {
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
        response.setChunks(List.of());
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
