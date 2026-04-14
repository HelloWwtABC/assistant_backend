package com.wwt.assistant.utils.parser;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class DocumentParserFactory {

    private final List<DocumentParser> parsers;

    public DocumentParser getParser(String fileType) {
        if (!StringUtils.hasText(fileType)) {
            throw new IllegalArgumentException("fileType must not be blank");
        }

        return parsers.stream()
                .filter(parser -> parser.supportsFileType(fileType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No document parser found for file type: " + fileType));
    }

    public DocumentParser getParserByFileName(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            throw new IllegalArgumentException("fileName must not be blank");
        }

        return parsers.stream()
                .filter(parser -> parser.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No document parser found for file name: " + fileName));
    }
}
