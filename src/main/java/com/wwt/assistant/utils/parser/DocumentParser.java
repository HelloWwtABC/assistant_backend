package com.wwt.assistant.utils.parser;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

public abstract class DocumentParser {

    /**
     * 判断是否支持该文件类型
     * @param fileName
     * @return
     */
    public final boolean supports(String fileName) {
        return supportsFileType(resolveFileType(fileName));
    }

    public final boolean supportsFileType(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            return false;
        }
        return supportedFileTypes().contains(normalizeFileType(fileType));
    }

    public final ParseResult parse(MultipartFile file) {
        Assert.notNull(file, "file must not be null");
        String fileName = file.getOriginalFilename();
        if (!StringUtils.hasText(fileName)) {
            return failure(null, null, "文件名不能为空");
        }
        if (file.isEmpty()) {
            return failure(fileName, resolveFileType(fileName), "文件内容不能为空");
        }

        try (InputStream inputStream = file.getInputStream()) {
            return parse(fileName, inputStream);
        } catch (Exception ex) {
            return failure(fileName, resolveFileType(fileName), buildErrorMessage(ex));
        }
    }

    public final ParseResult parse(String fileName, InputStream inputStream) {
        if (!StringUtils.hasText(fileName)) {
            return failure(null, null, "文件名不能为空");
        }
        if (inputStream == null) {
            return failure(fileName, resolveFileType(fileName), "文件输入流不能为空");
        }

        String normalizedFileName = fileName.trim();
        String fileType = resolveFileType(normalizedFileName);
        if (!supportsFileType(fileType)) {
            return failure(normalizedFileName, fileType, "当前解析器不支持该文件类型");
        }

        try {
            ParseResult result = doParse(normalizedFileName, fileType, inputStream);
            return normalizeResult(result, normalizedFileName, fileType);
        } catch (Exception ex) {
            return failure(normalizedFileName, fileType, buildErrorMessage(ex));
        }
    }

    protected abstract Set<String> supportedFileTypes();

    protected abstract ParseResult doParse(String fileName, String fileType, InputStream inputStream) throws Exception;

    protected ParseResult success(
            String fileName,
            String fileType,
            String content,
            Integer pageCount,
            List<ParseSection> sections,
            Map<String, Object> metadata) {
        String safeContent = content == null ? "" : content;
        List<ParseSection> safeSections = sections == null ? List.of() : sections;
        Map<String, Object> safeMetadata = metadata == null ? Map.of() : metadata;
        return ParseResult.builder()
                .success(true)
                .errorMessage(null)
                .fileName(fileName)
                .fileType(normalizeFileType(fileType))
                .content(safeContent)
                .contentLength(safeContent.length())
                .pageCount(pageCount)
                .sections(safeSections)
                .metadata(safeMetadata)
                .build();
    }

    protected ParseResult failure(String fileName, String fileType, String errorMessage) {
        return ParseResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .fileName(fileName)
                .fileType(normalizeFileType(fileType))
                .content("")
                .contentLength(0)
                .pageCount(0)
                .sections(List.of())
                .metadata(Map.of())
                .build();
    }

    protected ParseSection buildSection(Integer pageNo, String sectionTitle, String text) {
        return ParseSection.builder()
                .pageNo(pageNo)
                .sectionTitle(sectionTitle)
                .text(text)
                .build();
    }

    protected String resolveFileType(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return null;
        }
        String normalizedFileName = fileName.trim();
        int lastDotIndex = normalizedFileName.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == normalizedFileName.length() - 1) {
            return null;
        }
        return normalizeFileType(normalizedFileName.substring(lastDotIndex + 1));
    }

    protected String normalizeFileType(String fileType) {
        return StringUtils.hasText(fileType) ? fileType.trim().toLowerCase(Locale.ROOT) : null;
    }

    protected List<ParseSection> emptySections() {
        return Collections.emptyList();
    }

    protected Map<String, Object> emptyMetadata() {
        return Collections.emptyMap();
    }

    private ParseResult normalizeResult(ParseResult result, String fileName, String fileType) {
        if (result == null) {
            return failure(fileName, fileType, "解析结果不能为空");
        }

        String content = result.getContent() == null ? "" : result.getContent();
        Integer contentLength = result.getContentLength() != null ? result.getContentLength() : content.length();
        List<ParseSection> sections = result.getSections() == null ? List.of() : result.getSections();
        Map<String, Object> metadata = result.getMetadata() == null ? Map.of() : result.getMetadata();

        return ParseResult.builder()
                .success(result.isSuccess())
                .errorMessage(result.getErrorMessage())
                .fileName(StringUtils.hasText(result.getFileName()) ? result.getFileName() : fileName)
                .fileType(StringUtils.hasText(result.getFileType()) ? normalizeFileType(result.getFileType()) : fileType)
                .content(content)
                .contentLength(contentLength)
                .pageCount(result.getPageCount())
                .sections(sections)
                .metadata(metadata)
                .build();
    }

    private String buildErrorMessage(Exception ex) {
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "文档解析失败";
    }
}
