package com.wwt.assistant.utils.parser.impl;

import com.wwt.assistant.utils.parser.DocumentParser;
import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class MdDocumentParser extends DocumentParser {
    public static void main(String[] args) throws FileNotFoundException {
        DocumentParser mdDocumentParser = new MdDocumentParser();
        ParseResult result = mdDocumentParser.parse("未处理知识库文档处理任务清单.md", new FileInputStream("E:\\workspace\\vscode\\assistant-front\\related_file\\未处理知识库文档处理任务清单.md"));
        System.out.println(result.toString());
    }

    private static final String FILE_TYPE_MD = "md";
    private static final String DEFAULT_SECTION_TITLE = "正文";

    @Override
    protected Set<String> supportedFileTypes() {
        return Set.of(FILE_TYPE_MD);
    }

    @Override
    protected ParseResult doParse(String fileName, String fileType, InputStream inputStream) throws Exception {
        List<String> lines = readAllLines(inputStream);

        // Markdown 文档通常本身就是线性文本，这里先把全文完整保留下来，
        // 后续知识库切片、向量化或调试时都可以直接复用这份原始内容。
        String content = String.join("\n", lines);

        // 这里按 Markdown 标题切分段落：
        // 1. 遇到 "# / ## / ### ..." 认为进入一个新 section
        // 2. 标题前的普通正文，会被归入默认 section，避免内容丢失
        List<ParseSection> sections = splitSections(lines);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("lineCount", lines.size());
        metadata.put("sectionCount", sections.size());
        metadata.put("hasMarkdownHeading", sections.stream()
                .anyMatch(section -> !DEFAULT_SECTION_TITLE.equals(section.getSectionTitle())));

        // Markdown 没有天然“页码”概念，这里统一按单页文本返回 1。
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

    private List<ParseSection> splitSections(List<String> lines) {
        List<ParseSection> sections = new ArrayList<>();

        String currentTitle = DEFAULT_SECTION_TITLE;
        StringBuilder currentText = new StringBuilder();
        boolean seenHeading = false;

        for (String line : lines) {
            if (isMarkdownHeading(line)) {
                // 遇到新标题时，先把上一个 section 落下来，再开始新的 section。
                appendSectionIfNecessary(sections, currentTitle, currentText);
                currentTitle = extractHeadingTitle(line);
                currentText.setLength(0);
                seenHeading = true;
                continue;
            }

            appendLine(currentText, line);
        }

        appendSectionIfNecessary(sections, currentTitle, currentText);

        // 如果整个 Markdown 只有标题、没有正文，或者文件完全为空，
        // 这里仍然兜底返回一个空 section，保证调用方拿到稳定结构。
        if (sections.isEmpty()) {
            sections.add(buildSection(1, seenHeading ? currentTitle : DEFAULT_SECTION_TITLE, ""));
        }
        return sections;
    }

    /**
     * 判断一行是不是标准的 Markdown 标题
     * @param line
     * @return
     */
    private boolean isMarkdownHeading(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }

        int index = 0;
        while (index < trimmed.length() && trimmed.charAt(index) == '#') {
            index++;
        }
        return index < trimmed.length() && Character.isWhitespace(trimmed.charAt(index));
    }

    private String extractHeadingTitle(String line) {
        String trimmed = line.trim();
        int index = 0;
        while (index < trimmed.length() && trimmed.charAt(index) == '#') {
            index++;
        }
        String title = trimmed.substring(index).trim();
        return StringUtils.hasText(title) ? title : DEFAULT_SECTION_TITLE;
    }

    private void appendSectionIfNecessary(List<ParseSection> sections, String title, StringBuilder textBuffer) {
        String text = textBuffer.toString().trim();
        if (!StringUtils.hasText(text) && sections.isEmpty() && DEFAULT_SECTION_TITLE.equals(title)) {
            return;
        }

        if (!StringUtils.hasText(text) && StringUtils.hasText(title)) {
            sections.add(buildSection(1, title, ""));
            return;
        }

        sections.add(buildSection(1, title, text));
    }

    private void appendLine(StringBuilder textBuffer, String line) {
        if (textBuffer.length() > 0) {
            textBuffer.append('\n');
        }
        textBuffer.append(line);
    }
}
