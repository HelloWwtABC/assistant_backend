package com.wwt.assistant.utils;

import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class TextCleanerUtil {

    //// 1. 零宽字符（看不见但占位置的垃圾字符）
    private static final Pattern ZERO_WIDTH_PATTERN = Pattern.compile("[\\u200B-\\u200D\\uFEFF]");
    private static final Pattern CONTROL_CHAR_PATTERN = Pattern.compile("[\\p{Cntrl}&&[^\\r\\n\\t]]");
    // 3. 各种空格（英文空格、中文空格、制表符、换页符等）→ 统一变成1个空格
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("[ \\t\\x0B\\f\\u00A0\\u3000]+");
    private static final Pattern MULTI_BLANK_LINE_PATTERN = Pattern.compile("\\n{3,}");
    // 5. 乱码方块、特殊符号
    private static final Pattern GARBLED_SYMBOL_PATTERN = Pattern.compile("[�◆■□◇◼◻�]+");
    // 6. 页码（第 1 页 / page 1）→ 清理页眉页脚
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("第\\s*\\d+\\s*页|page\\s*\\d+", Pattern.CASE_INSENSITIVE);
    // 7. 无效标题（正文、段落1、第1页）→ 过滤垃圾标题
    private static final Pattern GENERIC_SECTION_TITLE_PATTERN = Pattern.compile("^(正文|段落\\d+|第\\d+页)$");
    // 8. 章节标题（第1章、1.1、1.1.1、第一条）→ 识别真正的章节
    private static final Pattern CHAPTER_TITLE_PATTERN =
            Pattern.compile("^(第[一二三四五六七八九十百千万0-9]+[章节篇部分卷条]|\\d+(\\.\\d+){0,3})[、.\\s：:].*");
    // 9. 列表项（- • * 1. ① 一、）→ 识别有序/无序列表
    private static final Pattern BULLET_LINE_PATTERN =
            Pattern.compile("^([-*•]|\\d+[.)、]|[（(]?[一二三四五六七八九十]+[)）、.])\\s*.+");

    private TextCleanerUtil() {
    }

    /**
     * 对原始文本做轻量清洗，适合没有结构化 section 的场景。
     */
    public static String clean(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }

        String normalized = normalizeBaseText(rawText);
        List<String> blocks = normalizeBlocks(normalized, Set.of());
        return String.join("\n\n", blocks).trim();
    }

    /**
     * 对解析后的结果做结构化清洗：
     * 1. 优先保留 section 语义边界
     * 2. 按 section 检测重复页眉页脚
     * 3. 重建出适合切片和检索的标准文本
     */
    public static ParseResult clean(ParseResult parseResult) {
        if (parseResult == null) {
            return null;
        }
        if (!parseResult.isSuccess()) {
            return parseResult;
        }

        List<ParseSection> originalSections = parseResult.getSections() == null ? List.of() : parseResult.getSections();
        Set<String> repeatedBoundaryLines = detectRepeatedBoundaryLines(originalSections);

        List<ParseSection> cleanedSections = new ArrayList<>();
        Set<String> seenSectionKeys = new LinkedHashSet<>();

        if (originalSections.isEmpty()) {
            String cleanedContent = clean(parseResult.getContent());
            cleanedSections.add(ParseSection.builder()
                    .pageNo(null)
                    .sectionTitle("正文")
                    .text(cleanedContent)
                    .build());
        } else {
            for (ParseSection section : originalSections) {
                ParseSection cleanedSection = cleanSection(section, repeatedBoundaryLines);
                if (cleanedSection == null) {
                    continue;
                }

                String sectionKey = buildSectionKey(cleanedSection);
                if (!seenSectionKeys.add(sectionKey)) {
                    continue;
                }
                cleanedSections.add(cleanedSection);
            }
        }

        String rebuiltContent = rebuildContent(cleanedSections);
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (parseResult.getMetadata() != null) {
            metadata.putAll(parseResult.getMetadata());
        }
        metadata.put("cleaned", true);
        metadata.put("cleanedSectionCount", cleanedSections.size());
        metadata.put("cleanedContentLength", rebuiltContent.length());

        return ParseResult.builder()
                .success(parseResult.isSuccess())
                .errorMessage(parseResult.getErrorMessage())
                .fileName(parseResult.getFileName())
                .fileType(parseResult.getFileType())
                .content(rebuiltContent)
                .contentLength(rebuiltContent.length())
                .pageCount(parseResult.getPageCount())
                .sections(cleanedSections)
                .metadata(metadata)
                .build();
    }

    private static ParseSection cleanSection(ParseSection section, Set<String> repeatedBoundaryLines) {
        if (section == null) {
            return null;
        }

        String cleanedTitle = cleanTitle(section.getSectionTitle());
        String normalizedText = normalizeBaseText(section.getText());
        List<String> blocks = normalizeBlocks(normalizedText, repeatedBoundaryLines);
        String cleanedText = String.join("\n\n", blocks).trim();

        if (!StringUtils.hasText(cleanedTitle) && !StringUtils.hasText(cleanedText)) {
            return null;
        }

        return ParseSection.builder()
                .pageNo(section.getPageNo())
                .sectionTitle(cleanedTitle)
                .text(cleanedText)
                .build();
    }

    private static String cleanTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String cleaned = normalizeBaseText(title).replace('\n', ' ').trim();
        return StringUtils.hasText(cleaned) ? cleaned : null;
    }

    private static String normalizeBaseText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }

        // 先统一换行和不可见字符，避免后续规则因为编码噪音失效。
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = ZERO_WIDTH_PATTERN.matcher(normalized).replaceAll("");
        normalized = CONTROL_CHAR_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.replace('\u00A0', ' ').replace('\u3000', ' ');
        normalized = GARBLED_SYMBOL_PATTERN.matcher(normalized).replaceAll("");

        String[] lines = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = MULTI_SPACE_PATTERN.matcher(lines[i]).replaceAll(" ").trim();
            builder.append(line);
            if (i < lines.length - 1) {
                builder.append('\n');
            }
        }

        normalized = builder.toString();
        normalized = MULTI_BLANK_LINE_PATTERN.matcher(normalized).replaceAll("\n\n");
        return normalized.trim();
    }

    private static List<String> normalizeBlocks(String text, Set<String> repeatedBoundaryLines) {
        List<String> blocks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return blocks;
        }

        String[] lines = text.split("\n");
        StringBuilder paragraphBuffer = new StringBuilder();

        for (String line : lines) {
            String trimmedLine = line.trim();
            if (shouldDropBoundaryLine(trimmedLine, repeatedBoundaryLines)) {
                continue;
            }

            if (!StringUtils.hasText(trimmedLine)) {
                flushParagraph(blocks, paragraphBuffer);
                continue;
            }

            if (looksLikeHeadingLine(trimmedLine) || looksLikeBulletLine(trimmedLine)) {
                flushParagraph(blocks, paragraphBuffer);
                blocks.add(trimmedLine);
                continue;
            }

            appendSentenceLine(paragraphBuffer, trimmedLine);
        }

        flushParagraph(blocks, paragraphBuffer);
        return deduplicateBlocks(blocks);
    }

    private static void appendSentenceLine(StringBuilder paragraphBuffer, String line) {
        if (paragraphBuffer.length() == 0) {
            paragraphBuffer.append(line);
            return;
        }

        // 解析器有时会把一句话错误拆到两行，这里做“轻量合并”：
        // 1. 如果上一行以连字符结尾，直接去掉连字符拼接
        // 2. 否则用空格连接，得到更连续的自然语言文本
        if (paragraphBuffer.charAt(paragraphBuffer.length() - 1) == '-') {
            paragraphBuffer.deleteCharAt(paragraphBuffer.length() - 1);
            paragraphBuffer.append(line);
            return;
        }

        paragraphBuffer.append(' ').append(line);
    }

    private static void flushParagraph(List<String> blocks, StringBuilder paragraphBuffer) {
        if (paragraphBuffer.length() == 0) {
            return;
        }

        String paragraph = paragraphBuffer.toString().trim();
        if (StringUtils.hasText(paragraph)) {
            blocks.add(paragraph);
        }
        paragraphBuffer.setLength(0);
    }

    private static List<String> deduplicateBlocks(List<String> blocks) {
        List<String> deduplicated = new ArrayList<>();
        Set<String> seenParagraphs = new LinkedHashSet<>();
        String previousKey = null;

        for (String block : blocks) {
            String normalizedKey = normalizeForComparison(block);
            if (!StringUtils.hasText(normalizedKey)) {
                continue;
            }

            // 连续重复的标题或段落直接丢弃；
            // 非连续但完全相同的长段落也跳过，减少解析器重复抽取造成的噪音。
            if (normalizedKey.equals(previousKey)) {
                continue;
            }
            if (!looksLikeHeadingLine(block) && block.length() >= 12 && !seenParagraphs.add(normalizedKey)) {
                continue;
            }

            deduplicated.add(block);
            previousKey = normalizedKey;
        }
        return deduplicated;
    }

    /**
     * 从每个章节的【开头 2 行 + 结尾 2 行】提取候选行，统计重复出现的页眉 / 页脚 / 重复标题
     * @param sections
     * @return
     */
    private static Set<String> detectRepeatedBoundaryLines(List<ParseSection> sections) {
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (ParseSection section : sections) {
            if (section == null || !StringUtils.hasText(section.getText())) {
                continue;
            }

            List<String> lines = section.getText().lines()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
            int limit = Math.min(2, lines.size());
            for (int i = 0; i < limit; i++) {
                addBoundaryCandidate(counter, lines.get(i));
            }
            for (int i = Math.max(limit, lines.size() - 2); i < lines.size(); i++) {
                addBoundaryCandidate(counter, lines.get(i));
            }
        }

        Set<String> repeatedLines = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            if (entry.getValue() >= 2) {
                repeatedLines.add(entry.getKey());
            }
        }
        return repeatedLines;
    }

    private static void addBoundaryCandidate(Map<String, Integer> counter, String line) {
        String candidate = normalizeBoundaryCandidate(line);
        if (!StringUtils.hasText(candidate)) {
            return;
        }
        if (candidate.length() > 60) {
            return;
        }
        counter.merge(candidate, 1, Integer::sum);
    }

    private static boolean shouldDropBoundaryLine(String line, Set<String> repeatedBoundaryLines) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String candidate = normalizeBoundaryCandidate(line);
        return repeatedBoundaryLines.contains(candidate)
                || (PAGE_NUMBER_PATTERN.matcher(line.toLowerCase(Locale.ROOT)).find() && line.length() <= 30);
    }

    private static String normalizeBoundaryCandidate(String line) {
        String normalized = normalizeForComparison(line).replaceAll("\\d+", "#");
        return normalized;
    }

    private static String normalizeForComparison(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return MULTI_SPACE_PATTERN.matcher(text.trim().toLowerCase(Locale.ROOT)).replaceAll(" ");
    }

    private static boolean looksLikeHeadingLine(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.startsWith("#")) {
            return true;
        }
        if (CHAPTER_TITLE_PATTERN.matcher(trimmed).matches()) {
            return true;
        }
        return trimmed.length() <= 30
                && !(trimmed.endsWith("。")
                || trimmed.endsWith(".")
                || trimmed.endsWith("；")
                || trimmed.endsWith(";")
                || trimmed.endsWith("，")
                || trimmed.endsWith(","));
    }

    private static boolean looksLikeBulletLine(String line) {
        return StringUtils.hasText(line) && BULLET_LINE_PATTERN.matcher(line.trim()).matches();
    }

    private static String rebuildContent(List<ParseSection> sections) {
        StringBuilder builder = new StringBuilder();
        for (ParseSection section : sections) {
            if (section == null) {
                continue;
            }

            String title = cleanTitle(section.getSectionTitle());
            String text = normalizeBaseText(section.getText());

            if (StringUtils.hasText(title) && !GENERIC_SECTION_TITLE_PATTERN.matcher(title).matches()) {
                appendBlock(builder, title);
            }
            if (StringUtils.hasText(text)) {
                appendBlock(builder, text);
            }
        }
        return builder.toString().trim();
    }

    private static void appendBlock(StringBuilder builder, String block) {
        if (!StringUtils.hasText(block)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("\n\n");
        }
        builder.append(block.trim());
    }

    private static String buildSectionKey(ParseSection section) {
        String title = normalizeForComparison(section.getSectionTitle());
        String text = normalizeForComparison(section.getText());
        return title + "||" + text;
    }
}
