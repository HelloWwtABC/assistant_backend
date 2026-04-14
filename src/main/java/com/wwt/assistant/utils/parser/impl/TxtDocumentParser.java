package com.wwt.assistant.utils.parser.impl;

import com.wwt.assistant.utils.parser.DocumentParser;
import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TxtDocumentParser extends DocumentParser {

    private static final String FILE_TYPE_TXT = "txt";
    private static final String DEFAULT_SECTION_PREFIX = "段落";

    @Override
    protected Set<String> supportedFileTypes() {
        return Set.of(FILE_TYPE_TXT);
    }

    @Override
    protected ParseResult doParse(String fileName, String fileType, InputStream inputStream) throws Exception {
        List<String> lines = readAllLines(inputStream);

        // TXT 文件没有显式结构，所以先完整保留全文，
        // 后续如果要做摘要、切片或回显，可以直接使用这份原始文本。
        String content = String.join("\n", lines);

        // 对纯文本来说，最常见的自然分段方式就是“空行分段”：
        // 连续非空行会被合并为一个 section，空行则视为段落边界。
        List<ParseSection> sections = splitParagraphs(lines);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("lineCount", lines.size());
        metadata.put("sectionCount", sections.size());
        metadata.put("blankLineSeparated", true);

        // 纯文本同样没有分页概念，这里统一按 1 页处理。
        return success(fileName, fileType, content, 1, sections, metadata);
    }

    private List<String> readAllLines(InputStream inputStream) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private List<ParseSection> splitParagraphs(List<String> lines) {
        List<ParseSection> sections = new ArrayList<>();
        StringBuilder currentParagraph = new StringBuilder();
        int sectionIndex = 1;

        for (String line : lines) {
            if (!StringUtils.hasText(line)) {
                // 遇到空行时，把当前累计的段落落下来。
                sectionIndex = appendParagraphIfNecessary(sections, currentParagraph, sectionIndex);
                continue;
            }

            appendLine(currentParagraph, line);
        }

        appendParagraphIfNecessary(sections, currentParagraph, sectionIndex);

        // 如果文件为空，或者只有空白字符，也兜底返回一个空 section，
        // 保证解析结果结构稳定。
        if (sections.isEmpty()) {
            sections.add(buildSection(1, DEFAULT_SECTION_PREFIX + "1", ""));
        }
        return sections;
    }

    private int appendParagraphIfNecessary(List<ParseSection> sections, StringBuilder currentParagraph, int sectionIndex) {
        String text = currentParagraph.toString().trim();
        if (!StringUtils.hasText(text)) {
            currentParagraph.setLength(0);
            return sectionIndex;
        }

        sections.add(buildSection(1, DEFAULT_SECTION_PREFIX + sectionIndex, text));
        currentParagraph.setLength(0);
        return sectionIndex + 1;
    }

    private void appendLine(StringBuilder textBuffer, String line) {
        if (textBuffer.length() > 0) {
            textBuffer.append('\n');
        }
        textBuffer.append(line);
    }
}
