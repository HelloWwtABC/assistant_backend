package com.wwt.assistant.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wwt.assistant.common.ApiResponse;
import com.wwt.assistant.common.CurrentUser;
import com.wwt.assistant.common.UserContextHolder;
import com.wwt.assistant.dto.vector.ChunkVectorUpsertRequest;
import com.wwt.assistant.entity.KbDocument;
import com.wwt.assistant.entity.KbDocumentChunk;
import com.wwt.assistant.mapper.DocumentChunkMapper;
import com.wwt.assistant.mapper.DocumentMapper;
import com.wwt.assistant.mapper.SysUserMapper;
import com.wwt.assistant.service.ArkModelService;
import com.wwt.assistant.service.DocumentService;
import com.wwt.assistant.service.QdrantVectorService;
import com.wwt.assistant.utils.MinioUtil;
import com.wwt.assistant.utils.parser.DocumentParserFactory;
import com.wwt.assistant.utils.parser.ParseResult;
import com.wwt.assistant.utils.parser.ParseSection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

@ExtendWith(MockitoExtension.class)
class DocumentServiceImplTest {

    @Mock
    private DocumentMapper documentMapper;

    @Mock
    private DocumentChunkMapper documentChunkMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private MinioUtil minioUtil;

    @Mock
    private ArkModelService arkModelService;

    @Mock
    private QdrantVectorService qdrantVectorService;

    @Mock
    private DocumentParserFactory documentParserFactory;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private DocumentService asyncDocumentService;

    @InjectMocks
    private DocumentServiceImpl documentService;

    @Test
    void shouldReplaceOldChunksAndPersistContinuousIndexes() {
        Long teamId = 88L;
        Long documentId = 101L;
        ParseResult parseResult = buildParseResult();
        when(applicationContext.getBean(DocumentService.class)).thenReturn(asyncDocumentService);
        when(documentMapper.selectDocumentForProcessing(teamId, documentId)).thenReturn(buildDocument(documentId));
        when(documentChunkMapper.deleteByDocumentId(documentId)).thenReturn(2);
        when(documentChunkMapper.batchInsert(any())).thenAnswer(invocation -> {
            List<?> chunks = invocation.getArgument(0);
            return chunks.size();
        });
        when(documentMapper.updateChunkingResult(eq(documentId), any(), eq("chunked"), eq(null), any(), any(), any()))
                .thenReturn(1);

        int firstCount = documentService.chunkAndSaveDocument(teamId, documentId, parseResult);
        int secondCount = documentService.chunkAndSaveDocument(teamId, documentId, parseResult);

        assertEquals(firstCount, secondCount);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<KbDocumentChunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(documentChunkMapper, times(2)).batchInsert(captor.capture());
        List<KbDocumentChunk> savedChunks = captor.getAllValues().get(0);
        assertEquals(firstCount, savedChunks.size());
        for (int i = 0; i < savedChunks.size(); i++) {
            KbDocumentChunk chunk = savedChunks.get(i);
            assertEquals(i + 1, chunk.getChunkIndex());
            assertEquals(documentId, chunk.getDocumentId());
            assertEquals(2001L, chunk.getKnowledgeBaseId());
            assertEquals(chunk.getContent().trim(), chunk.getContent());
        }

        InOrder inOrder = inOrder(documentMapper, documentChunkMapper);
        inOrder.verify(documentMapper).selectDocumentForProcessing(teamId, documentId);
        inOrder.verify(documentChunkMapper).deleteByDocumentId(documentId);
        inOrder.verify(documentChunkMapper).batchInsert(any());
        inOrder.verify(documentMapper).updateChunkingResult(eq(documentId), any(), eq("chunked"), eq(null), any(), any(), any());
        verify(asyncDocumentService, times(2)).embedAndUpsertDocumentChunks(eq(teamId), any());
    }

    @Test
    void shouldThrowWhenInsertedCountDoesNotMatchGeneratedCount() {
        Long teamId = 88L;
        Long documentId = 202L;
        when(documentMapper.selectDocumentForProcessing(teamId, documentId)).thenReturn(buildDocument(documentId));
        when(documentChunkMapper.deleteByDocumentId(documentId)).thenReturn(0);
        when(documentChunkMapper.batchInsert(any())).thenReturn(1);

        assertThrows(IllegalStateException.class, () -> documentService.chunkAndSaveDocument(teamId, documentId, buildParseResult()));
        verify(documentMapper, never()).updateChunkingResult(eq(documentId), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldEmbedAndUpsertChunksWithTeamScopedPayload() {
        Long teamId = 88L;
        Long documentId = 303L;
        Long knowledgeBaseId = 2001L;
        List<KbDocumentChunk> chunks = List.of(
                buildChunk(501L, documentId, knowledgeBaseId, 1, "chunk one"),
                buildChunk(502L, documentId, knowledgeBaseId, 2, "chunk two"));
        KbDocument document = buildDocument(documentId);
        document.setFileType("pdf");

        when(documentMapper.selectVectorizationDocument(teamId, documentId, knowledgeBaseId)).thenReturn(document);
        when(documentMapper.updateDocumentStatus(eq(documentId), eq("vectorizing"), any())).thenReturn(1);
        when(documentMapper.updateDocumentStatus(eq(documentId), eq("completed"), any())).thenReturn(1);
        when(arkModelService.createEmbeddings(any())).thenReturn(List.of(
                List.of(0.1F, 0.2F, 0.3F),
                List.of(0.4F, 0.5F, 0.6F)));

        documentService.embedAndUpsertDocumentChunks(teamId, chunks);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChunkVectorUpsertRequest>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantVectorService).upsertChunks(captor.capture());
        List<ChunkVectorUpsertRequest> requests = captor.getValue();
        assertEquals(2, requests.size());
        assertEquals(teamId, requests.get(0).getTeamId());
        assertEquals(documentId, requests.get(0).getDocumentId());
        assertEquals(knowledgeBaseId, requests.get(0).getKnowledgeBaseId());
        assertEquals(501L, requests.get(0).getChunkId());
        assertEquals("pdf", requests.get(0).getFileType());
        verify(documentMapper).updateDocumentStatus(eq(documentId), eq("vectorizing"), any());
        verify(documentMapper).updateDocumentStatus(eq(documentId), eq("completed"), any());
        verify(documentChunkMapper).markVectorCompleted(List.of(501L, 502L));
    }

    @Test
    void shouldDeleteDocumentAndScheduleExternalCleanup() {
        Long userId = 7L;
        Long teamId = 88L;
        Long documentId = 404L;
        KbDocument document = buildDocument(documentId);
        document.setFileUrl("88/test.pdf");

        UserContextHolder.setCurrentUser(new CurrentUser(userId, "tester", teamId));
        try {
            when(applicationContext.getBean(DocumentService.class)).thenReturn(asyncDocumentService);
            when(documentMapper.selectDocumentsForDeletion(teamId, List.of(documentId))).thenReturn(List.of(document));
            when(documentMapper.markDocumentsDeleting(eq(List.of(documentId)), any(), any())).thenReturn(1);
            when(documentMapper.softDeleteDocuments(eq(List.of(documentId)), any(), eq(userId), any())).thenReturn(1);
            when(documentMapper.decreaseDocumentCount(teamId, 2001L, 1)).thenReturn(1);

            ApiResponse<Void> response = documentService.deleteDocument(String.valueOf(documentId));

            assertEquals(ApiResponse.SUCCESS_CODE, response.getCode());
            verify(documentChunkMapper).softDeleteByDocumentIds(eq(List.of(documentId)), any(), eq(userId), any());
            verify(asyncDocumentService).cleanupDeletedDocumentResourcesAsync(teamId, documentId, "88/test.pdf");
        } finally {
            UserContextHolder.clear();
        }
    }

    private ParseResult buildParseResult() {
        return ParseResult.builder()
                .success(true)
                .sections(List.of(
                        ParseSection.builder()
                                .pageNo(1)
                                .sectionTitle("Overview")
                                .text("Background paragraph.\n\nGoal paragraph.")
                                .build(),
                        ParseSection.builder()
                                .pageNo(null)
                                .sectionTitle("Body")
                                .text("Implementation details paragraph.")
                                .build()))
                .build();
    }

    private KbDocument buildDocument(Long documentId) {
        KbDocument document = new KbDocument();
        document.setId(documentId);
        document.setKnowledgeBaseId(2001L);
        return document;
    }

    private KbDocumentChunk buildChunk(
            Long chunkId,
            Long documentId,
            Long knowledgeBaseId,
            Integer chunkIndex,
            String content) {
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(chunkId);
        chunk.setDocumentId(documentId);
        chunk.setKnowledgeBaseId(knowledgeBaseId);
        chunk.setChunkIndex(chunkIndex);
        chunk.setContent(content);
        return chunk;
    }
}
