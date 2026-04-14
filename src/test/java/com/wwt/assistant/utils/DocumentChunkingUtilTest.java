package com.wwt.assistant.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wwt.assistant.utils.DocumentChunkingUtil.TextChunk;
import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentChunkingUtilTest {

    @Test
    void shouldSplitBySectionAndParagraph() {
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .sections(List.of(
                        ParseSection.builder()
                                .pageNo(1)
                                .sectionTitle("第一章 概述")
                                .text("第一段介绍系统背景。\n\n第二段补充系统目标。")
                                .build(),
                        ParseSection.builder()
                                .pageNo(2)
                                .sectionTitle("第二章 细节")
                                .text("这里是细节说明。")
                                .build()))
                .build();

        List<TextChunk> chunks = DocumentChunkingUtil.split(parseResult, 60, 100, 20);

        assertEquals(2, chunks.size());
        assertEquals("第一章 概述", chunks.get(0).sectionTitle());
        assertEquals(Integer.valueOf(1), chunks.get(0).pageNo());
        assertTrue(chunks.get(0).content().contains("第一段介绍系统背景"));
        assertEquals("第二章 细节", chunks.get(1).sectionTitle());
        assertEquals(Integer.valueOf(2), chunks.get(1).pageNo());
    }

    @Test
    void shouldSplitOverLongParagraphByWindowWhenNeeded() {
        String longSentence = "这是一段没有明显句号边界的超长文本".repeat(30);
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .sections(List.of(ParseSection.builder()
                        .pageNo(3)
                        .sectionTitle("长段落")
                        .text(longSentence)
                        .build()))
                .build();

        List<TextChunk> chunks = DocumentChunkingUtil.split(parseResult, 80, 120, 20);

        assertTrue(chunks.size() > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().length() <= 80));
        assertTrue(chunks.stream().allMatch(chunk -> chunk.content().trim().length() > 0));
        assertTrue(chunks.stream().allMatch(chunk -> Integer.valueOf(3).equals(chunk.pageNo())));
    }

    @Test
    void shouldKeepNullPageNoWhenParserCannotProvideIt() {
        ParseResult parseResult = ParseResult.builder()
                .success(true)
                .sections(List.of(ParseSection.builder()
                        .pageNo(null)
                        .sectionTitle("正文")
                        .text("只有一段普通内容。")
                        .build()))
                .build();

        List<TextChunk> chunks = DocumentChunkingUtil.split(parseResult, 50, 80, 10);

        assertEquals(1, chunks.size());
        assertNull(chunks.get(0).pageNo());
        assertNull(chunks.get(0).sectionTitle());
    }
}
