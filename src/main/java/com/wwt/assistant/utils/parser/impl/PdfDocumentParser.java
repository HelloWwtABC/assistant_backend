package com.wwt.assistant.utils.parser.impl;

import com.wwt.assistant.utils.parser.DocumentParser;
import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
public class PdfDocumentParser extends DocumentParser {
    public static void main(String[] args) throws FileNotFoundException {
        DocumentParser pdfDocumentParser = new PdfDocumentParser();
        ParseResult result = pdfDocumentParser.parse("SNUNet-CD_ A Densely_.ection of VHR Images.pdf", new FileInputStream("E:\\download\\browser_download\\文献\\SNUNet-CD_ A Densely_.ection of VHR Images.pdf"));
        System.out.println(result.toString());
    }

    private static final String FILE_TYPE_PDF = "pdf";
    private static final String DEFAULT_PAGE_TITLE_PREFIX = "第";
    private static final String PAGE_COUNT_KEY = "xmpTPg:NPages";
    private static final String TITLE_KEY = "dc:title";
    private static final String AUTHOR_KEY = "meta:author";

    @Override
    protected Set<String> supportedFileTypes() {
        return Set.of(FILE_TYPE_PDF);
    }

    @Override
    protected ParseResult doParse(String fileName, String fileType, InputStream inputStream) throws Exception {
        Metadata metadata = new Metadata();
        String content = extractText(inputStream, metadata);

        // Tika 在解析 PDF 时，通常会在页与页之间插入 form-feed（\f）分隔符。
        // 这里利用这个特性把全文拆成“每页一个 section”，便于你后续理解 PDF 解析后的结构。
        List<ParseSection> sections = splitPages(content);
        Integer pageCount = resolvePageCount(metadata, sections);

        Map<String, Object> resultMetadata = new LinkedHashMap<>();
        resultMetadata.put("pageCount", pageCount);
        resultMetadata.put("sectionCount", sections.size());
        resultMetadata.put("contentType", metadata.get(Metadata.CONTENT_TYPE));
        resultMetadata.put("title", metadata.get(TITLE_KEY));
        resultMetadata.put("author", metadata.get(AUTHOR_KEY));

        return success(fileName, fileType, content, pageCount, sections, resultMetadata);
    }

    private String extractText(InputStream inputStream, Metadata metadata) throws Exception {
        // BodyContentHandler(-1) 表示不限制提取文本长度。
        // 对知识库解析来说，我们希望尽量保留全文，而不是被默认长度截断。
        ContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        ParseContext parseContext = new ParseContext();
        parser.parse(inputStream, handler, metadata, parseContext);
        return handler.toString().trim();
    }

    private List<ParseSection> splitPages(String content) {
        List<ParseSection> sections = new ArrayList<>();
        if (!StringUtils.hasText(content)) {
            sections.add(buildSection(1, DEFAULT_PAGE_TITLE_PREFIX + 1 + "页", ""));
            return sections;
        }

        String[] pages = content.split("\\f");
        int pageNo = 1;
        for (String pageText : pages) {
            String normalizedPageText = pageText == null ? "" : pageText.trim();
            sections.add(buildSection(pageNo, DEFAULT_PAGE_TITLE_PREFIX + pageNo + "页", normalizedPageText));
            pageNo++;
        }
        return sections;
    }

    private Integer resolvePageCount(Metadata metadata, List<ParseSection> sections) {
        String pageCountValue = metadata.get(PAGE_COUNT_KEY);
        if (StringUtils.hasText(pageCountValue)) {
            try {
                return Integer.parseInt(pageCountValue.trim());
            } catch (NumberFormatException ignored) {
                // 如果元数据里的页数格式异常，就退回到 section 数量兜底。
            }
        }
        return sections.size();
    }
}
