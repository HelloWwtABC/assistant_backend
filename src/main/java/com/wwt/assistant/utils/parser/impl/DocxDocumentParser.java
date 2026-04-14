package com.wwt.assistant.utils.parser.impl;

import com.wwt.assistant.utils.parser.DocumentParser;
import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.xml.sax.ContentHandler;

@Component
public class DocxDocumentParser extends DocumentParser {

    private static final String FILE_TYPE_DOCX = "docx";
    private static final String DEFAULT_SECTION_PREFIX = "段落";
    private static final String TITLE_KEY = "dc:title";
    private static final String AUTHOR_KEY = "meta:author";
    private static final String CREATOR_KEY = "dc:creator";

    @Override
    protected Set<String> supportedFileTypes() {
        return Set.of(FILE_TYPE_DOCX);
    }

    @Override
    protected ParseResult doParse(String fileName, String fileType, InputStream inputStream) throws Exception {
        Metadata metadata = new Metadata();
        String content = extractText(inputStream, metadata);

        // 对 DOCX 而言，Tika 会把 Word 文档转换成普通文本。
        // 我们这里不直接尝试恢复 Word 里的复杂样式，而是用“空行分段”的方式保留自然阅读结构。
        List<ParseSection> sections = splitParagraphBlocks(content);

        Map<String, Object> resultMetadata = new LinkedHashMap<>();
        resultMetadata.put("sectionCount", sections.size());
        resultMetadata.put("contentType", metadata.get(Metadata.CONTENT_TYPE));
        resultMetadata.put("title", metadata.get(TITLE_KEY));
        resultMetadata.put("author", metadata.get(AUTHOR_KEY));
        resultMetadata.put("creator", metadata.get(CREATOR_KEY));

        // DOCX 同样没有固定“页码”可稳定提取，这里统一按单页文档处理。
        return success(fileName, fileType, content, 1, sections, resultMetadata);
    }

    private String extractText(InputStream inputStream, Metadata metadata) throws Exception {
        ContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext parseContext = new ParseContext();
        parser.parse(inputStream, handler, metadata, parseContext);
        return handler.toString().trim();
    }

    private List<ParseSection> splitParagraphBlocks(String content) {
        List<ParseSection> sections = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            sections.add(buildSection(1, DEFAULT_SECTION_PREFIX + 1, ""));
            return sections;
        }

        // Word 转文本后，通常会保留连续空行。
        // 这里按“一个或多个空白行”切段，得到更接近自然段的 section。
        String[] blocks = content.split("(\\r?\\n)\\s*(\\r?\\n)+");
        int sectionIndex = 1;
        for (String block : blocks) {
            String normalizedBlock = block == null ? "" : block.trim();
            if (!StringUtils.hasText(normalizedBlock)) {
                continue;
            }

            String title = detectSectionTitle(normalizedBlock, sectionIndex);
            sections.add(buildSection(1, title, normalizedBlock));
            sectionIndex++;
        }

        if (sections.isEmpty()) {
            sections.add(buildSection(1, DEFAULT_SECTION_PREFIX + 1, ""));
        }
        return sections;
    }

    private String detectSectionTitle(String block, int sectionIndex) {
        // 这里做一个“轻量标题猜测”：
        // 如果一个段落块的第一行很短、且不像普通句子，就把它当 section 标题；
        // 否则就退回到“段落N”这种稳定命名。
        String firstLine = block.lines()
                .findFirst()
                .map(String::trim)
                .orElse("");

        if (looksLikeHeading(firstLine)) {
            return firstLine;
        }
        return DEFAULT_SECTION_PREFIX + sectionIndex;
    }

    private boolean looksLikeHeading(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (text.length() > 30) {
            return false;
        }
        return !(text.endsWith("。")
                || text.endsWith(".")
                || text.endsWith("；")
                || text.endsWith(";")
                || text.endsWith("，")
                || text.endsWith(",")
                || text.endsWith("：")
                || text.endsWith(":"));
    }
}
