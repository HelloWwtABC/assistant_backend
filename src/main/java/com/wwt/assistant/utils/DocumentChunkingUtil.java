package com.wwt.assistant.utils;

import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * Splits cleaned document text into semantically meaningful chunks.
 *
 * <p>The overall strategy is:
 * 1. Respect parser sections first.
 * 2. Prefer paragraph boundaries inside a section.
 * 3. For oversized paragraphs, fallback to sentence boundaries.
 * 4. If one sentence is still too long, use a sliding window with overlap.
 */
public final class DocumentChunkingUtil {

    public static final int DEFAULT_TARGET_CHUNK_SIZE = 800;
    public static final int DEFAULT_MAX_CHUNK_SIZE = 1200;
    public static final int DEFAULT_OVERLAP_SIZE = 150;

    private static final int MIN_MERGE_RATIO_DIVISOR = 2;
    private static final Pattern PARAGRAPH_SPLIT_PATTERN = Pattern.compile("\\n{2,}");
    private static final Pattern GENERIC_TITLE_PATTERN = Pattern.compile("^(正文|段落\\d+|第\\d+页)$");

    private DocumentChunkingUtil() {
    }

    public static List<TextChunk> split(ParseResult parseResult) {
        return split(parseResult, DEFAULT_TARGET_CHUNK_SIZE, DEFAULT_MAX_CHUNK_SIZE, DEFAULT_OVERLAP_SIZE);
    }

    public static List<TextChunk> split(ParseResult parseResult, int targetChunkSize, int maxChunkSize, int overlapSize) {
        validateArguments(parseResult, targetChunkSize, maxChunkSize, overlapSize);

        List<TextChunk> chunks = new ArrayList<>();
        List<SectionCarrier> sections = resolveSections(parseResult);
        for (SectionCarrier section : sections) {
            chunks.addAll(splitSection(section, targetChunkSize, maxChunkSize, overlapSize));
        }
        return chunks;
    }

    private static void validateArguments(ParseResult parseResult, int targetChunkSize, int maxChunkSize, int overlapSize) {
        if (parseResult == null) {
            throw new IllegalArgumentException("parseResult must not be null");
        }
        if (!parseResult.isSuccess()) {
            throw new IllegalArgumentException("parseResult must be successful before chunking");
        }
        if (targetChunkSize <= 0 || maxChunkSize <= 0) {
            throw new IllegalArgumentException("chunk size must be greater than 0");
        }
        if (targetChunkSize > maxChunkSize) {
            throw new IllegalArgumentException("targetChunkSize must not be greater than maxChunkSize");
        }
        if (overlapSize < 0 || overlapSize >= targetChunkSize) {
            throw new IllegalArgumentException("overlapSize must be between 0 and targetChunkSize");
        }
    }

    private static List<SectionCarrier> resolveSections(ParseResult parseResult) {
        List<SectionCarrier> sections = new ArrayList<>();
        if (parseResult.getSections() != null) {
            for (ParseSection section : parseResult.getSections()) {
                if (section == null || !StringUtils.hasText(section.getText())) {
                    continue;
                }
                sections.add(new SectionCarrier(section.getPageNo(), normalizeTitle(section.getSectionTitle()), section.getText().trim()));
            }
        }

        if (!sections.isEmpty()) {
            return sections;
        }

        if (StringUtils.hasText(parseResult.getContent())) {
            sections.add(new SectionCarrier(null, null, parseResult.getContent().trim()));
        }
        return sections;
    }

    /**
     * 划分单个Section
     * @param section
     * @param targetChunkSize
     * @param maxChunkSize
     * @param overlapSize
     * @return
     */
    private static List<TextChunk> splitSection(
            SectionCarrier section,
            int targetChunkSize,
            int maxChunkSize,
            int overlapSize) {
        List<TextChunk> chunks = new ArrayList<>();
        // 1 先将段落都划出来
        List<String> paragraphs = splitParagraphs(section.text());
        StringBuilder currentChunk = new StringBuilder();

        // 2 遍历所有段落，进行组装
        for (String paragraph : paragraphs) {
            if (!StringUtils.hasText(paragraph)) {
                continue;
            }

            String normalizedParagraph = paragraph.trim();
            // 2.1单个段落长度大于maxChunkSize
            if (normalizedParagraph.length() > maxChunkSize) {
                // 2.1.1 之前处理的chunk进行保存
                flushChunk(chunks, currentChunk, section.pageNo(), section.sectionTitle());
                // 2.1.2 将长段落划分成多个chunk进行保存
                chunks.addAll(splitLongParagraph(section, normalizedParagraph, targetChunkSize, maxChunkSize, overlapSize));
                continue;
            }

            // 3 智能追加段落：能加就加，加不下就提交当前块
            appendParagraphOrFlush(
                    chunks,
                    currentChunk,
                    normalizedParagraph,
                    section.pageNo(),
                    section.sectionTitle(),
                    targetChunkSize,
                    maxChunkSize);
        }
        // 4 循环结束，把最后剩下的块提交保存
        flushChunk(chunks, currentChunk, section.pageNo(), section.sectionTitle());
        return chunks;
    }

    /**
     * 将一块文本按照段落划分
     * @param text
     * @return
     */
    private static List<String> splitParagraphs(String text) {
        List<String> paragraphs = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return paragraphs;
        }

        String[] blocks = PARAGRAPH_SPLIT_PATTERN.split(text.trim());
        for (String block : blocks) {
            String normalized = block == null ? null : block.trim();
            if (StringUtils.hasText(normalized)) {
                paragraphs.add(normalized);
            }
        }
        return paragraphs.isEmpty() ? List.of(text.trim()) : paragraphs;
    }

    /**
     * 智能追加段落：能加就加，加不下就提交当前块
     * @param chunks 最终的文本块列表（存结果）
     * @param currentChunk 正在拼接的当前块（缓冲）
     * @param paragraph 要追加的段落文本
     * @param pageNo 页码
     * @param sectionTitle 章节标题
     * @param targetChunkSize 目标块大小（期望大小）
     * @param maxChunkSize 最大块大小（硬限制，绝对不超）
     */
    private static void appendParagraphOrFlush(
            List<TextChunk> chunks,
            StringBuilder currentChunk,
            String paragraph,
            Integer pageNo,
            String sectionTitle,
            int targetChunkSize,
            int maxChunkSize) {

        // 1. 如果当前块是空的 → 直接把段落放进去
        if (currentChunk.length() == 0) {
            currentChunk.append(paragraph);
            return;
        }

        // 2. 计算：当前块 + 换行 + 新段落 的总长度
        int candidateLength = currentChunk.length() + 2 + paragraph.length();

        // 3. 如果追加后 ≤ 目标大小 → 直接加（用两个换行分隔段落）
        if (candidateLength <= targetChunkSize) {
            currentChunk.append("\n\n").append(paragraph);
            return;
        }

        // 4. 【柔性扩容规则】
        // 目标大小只是期望，不是硬限制
        // 如果当前块还很小（小于目标的 1/2 或 1/3）
        // 并且总长度不超过最大限制 → 允许再追加一个段落
        if (currentChunk.length() < (targetChunkSize / MIN_MERGE_RATIO_DIVISOR)
                && candidateLength <= maxChunkSize) {

            currentChunk.append("\n\n").append(paragraph);
            return;
        }

        // 5. 既不能按目标大小加，也不能柔性扩容 → 提交当前块
        flushChunk(chunks, currentChunk, pageNo, sectionTitle);

        // 6. 把新段落作为新块的开始
        currentChunk.append(paragraph);
    }

    /**
     * 划分超长段落
     * @param section
     * @param paragraph
     * @param targetChunkSize
     * @param maxChunkSize
     * @param overlapSize
     * @return
     */
    private static List<TextChunk> splitLongParagraph(
            SectionCarrier section,
            String paragraph,
            int targetChunkSize,
            int maxChunkSize,
            int overlapSize) {
        List<TextChunk> chunks = new ArrayList<>();
        // 1 划成句子
        List<String> sentences = splitSentences(paragraph);
        // 2 仅有一个超长句子，直接按固定窗口动态划分
        if (sentences.size() <= 1) {
            return splitByFixedWindow(paragraph, section.pageNo(), section.sectionTitle(), targetChunkSize, overlapSize);
        }

        StringBuilder currentChunk = new StringBuilder();
        // 3 遍历句子组装
        for (String sentence : sentences) {
            String normalizedSentence = sentence.trim();
            if (!StringUtils.hasText(normalizedSentence)) {
                continue;
            }

            // 4 超长句子，直接按固定窗口动态划分
            if (normalizedSentence.length() > maxChunkSize) {
                // 5 处理超长句子前，先处理之前剩下的
                flushChunk(chunks, currentChunk, section.pageNo(), section.sectionTitle());
                chunks.addAll(splitByFixedWindow(
                        normalizedSentence,
                        section.pageNo(),
                        section.sectionTitle(),
                        targetChunkSize,
                        overlapSize));
                continue;
            }

            if (currentChunk.length() == 0) {
                currentChunk.append(normalizedSentence);
                continue;
            }
            // 6 判断当前句子是否加入当前chunk
            int candidateLength = currentChunk.length() + 1 + normalizedSentence.length();
            if (candidateLength <= targetChunkSize) {
                currentChunk.append(' ').append(normalizedSentence);
                continue;
            }
            // 7 当前句子不能加入当前chunk，处理后，将距句子加入下一个chunk
            flushChunk(chunks, currentChunk, section.pageNo(), section.sectionTitle());
            currentChunk.append(normalizedSentence);
        }

        flushChunk(chunks, currentChunk, section.pageNo(), section.sectionTitle());
        return chunks;
    }

    /**
     * 将一段文本的句子全部分出来
     * @param paragraph
     * @return
     */
    private static List<String> splitSentences(String paragraph) {
        List<String> sentences = new ArrayList<>();
        if (!StringUtils.hasText(paragraph)) {
            return sentences;
        }

        StringBuilder currentSentence = new StringBuilder();
        for (int i = 0; i < paragraph.length(); i++) {
            char currentChar = paragraph.charAt(i);
            currentSentence.append(currentChar);
            if (isSentenceBoundary(paragraph, i)) {
                addSentence(sentences, currentSentence);
            }
        }
        addSentence(sentences, currentSentence);
        return sentences;
    }

    /**
     * 判断是否是一句话的结束
     * @param text
     * @param index
     * @return
     */
    private static boolean isSentenceBoundary(String text, int index) {
        char currentChar = text.charAt(index);
        if ("。！？!?；;".indexOf(currentChar) >= 0) {
            return true;
        }
        if (currentChar != '.') {
            return false;
        }

        if (index == text.length() - 1) {
            return true;
        }
        char nextChar = text.charAt(index + 1);
        return Character.isWhitespace(nextChar) || nextChar == '"' || nextChar == '\'';
    }

    private static void addSentence(List<String> sentences, StringBuilder currentSentence) {
        String normalized = currentSentence.toString().trim();
        if (StringUtils.hasText(normalized)) {
            sentences.add(normalized);
        }
        currentSentence.setLength(0);
    }

    /**
     * 固定窗口滑动 + 智能断句 文本切块
     * @param text 原始长文本
     * @param pageNo 页码（传给TextChunk）
     * @param sectionTitle 章节标题（传给TextChunk）
     * @param targetChunkSize 目标每块大小（如512、1024字符）
     * @param overlapSize 块之间重叠长度（防止句子被切断）
     * @return 分好的 TextChunk 列表
     */
    private static List<TextChunk> splitByFixedWindow(
            String text,
            Integer pageNo,
            String sectionTitle,
            int targetChunkSize,
            int overlapSize) {

        // 1. 初始化结果集合
        List<TextChunk> chunks = new ArrayList<>();

        // 2. 空值处理：文本为null就转空字符串，并且trim去空格
        String normalized = text == null ? "" : text.trim();

        // 3. 如果是空文本，直接返回空列表
        if (!StringUtils.hasText(normalized)) {
            return chunks;
        }

        // 4. 窗口大小 = 目标块大小
        int windowSize = targetChunkSize;

        // 5. 安全重叠：不能超过窗口大小-1，避免重叠过大卡死
        int safeOverlap = Math.min(overlapSize, Math.max(0, windowSize - 1));

        // 6. 滑动窗口起始位置，从0开始
        int start = 0;

        // 7. 开始滑动：只要start没到文本末尾就继续切
        while (start < normalized.length()) {

            // 8. 原始结束位置 = start + 窗口大小，但不能超过文本长度
            int rawEnd = Math.min(normalized.length(), start + windowSize);

            // 9. 核心：调用刚才的方法，智能调整结束位置（不在句子中间切断）
            int end = adjustWindowEnd(normalized, start, rawEnd);

            // 10. 兜底：如果调整后end无效，就用原始rawEnd
            if (end <= start) {
                end = rawEnd;
            }

            // 11. 截取 [start, end) 之间的文本，并trim
            String piece = normalized.substring(start, end).trim();

            // 12. 如果截取的片段有效，就封装成TextChunk加入结果
            if (StringUtils.hasText(piece)) {
                chunks.add(new TextChunk(piece, pageNo, sectionTitle));
            }

            // 13. 如果已经切到文本末尾，直接结束循环
            if (end >= normalized.length()) {
                break;
            }

            // 14. 计算下一个start：向前重叠 safeOverlap 长度，保证句子连贯
            // 下一块的起点：优先重叠一段，但至少往前挪 1 个字符，保证滑动、不卡死。
            int nextStart = Math.max(end - safeOverlap, start + 1);

            // 15. 防止死循环：nextStart 不能 <= start
            if (nextStart <= start) {
                nextStart = end;
            }

            // 16. 滑动窗口，进入下一轮切块
            start = nextStart;
        }

        // 17. 返回所有切好的文本块
        return chunks;
    }

    /**
     * 切割的时候，尽量不给句子给搞断，保证语义完整
     * @param text
     * @param start
     * @param rawEnd
     * @return
     */
    private static int adjustWindowEnd(String text, int start, int rawEnd) {
        if (rawEnd >= text.length()) {
            return rawEnd;
        }

        int minBreakIndex = start + Math.max(1, (rawEnd - start) / MIN_MERGE_RATIO_DIVISOR);
        for (int i = rawEnd; i > minBreakIndex; i--) {
            char currentChar = text.charAt(i - 1);
            if (Character.isWhitespace(currentChar) || "。！？!?；;,.，".indexOf(currentChar) >= 0) {
                return i;
            }
        }
        return rawEnd;
    }

    private static void flushChunk(List<TextChunk> chunks, StringBuilder currentChunk, Integer pageNo, String sectionTitle) {
        if (currentChunk.length() == 0) {
            return;
        }

        String content = currentChunk.toString().trim();
        if (StringUtils.hasText(content)) {
            chunks.add(new TextChunk(content, pageNo, sectionTitle));
        }
        currentChunk.setLength(0);
    }

    private static String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }

        String normalized = title.trim();
        if (!StringUtils.hasText(normalized) || GENERIC_TITLE_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private record SectionCarrier(Integer pageNo, String sectionTitle, String text) {
    }

    public record TextChunk(String content, Integer pageNo, String sectionTitle) {
    }
}
