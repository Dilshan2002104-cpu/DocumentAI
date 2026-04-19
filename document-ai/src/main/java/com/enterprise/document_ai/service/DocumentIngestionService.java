package com.enterprise.document_ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Media;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final FirestoreService firestoreService;
    private final VertexAiGeminiChatModel chatModel;

    private static final String SMART_EXTRACT_PROMPT = """
            Extract all text and structured information from this PDF page.
            - Format any tables as Markdown tables.
            - Describe any images or diagrams in detail.
            - Output only the Markdown content.
            """;

    public void ingestPdf(MultipartFile file, String userId) throws IOException {
        log.info("Starting SMART ingestion for: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

        String documentId = UUID.randomUUID().toString();
        List<Document> pageDocuments = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            int totalPages = document.getNumberOfPages();
            
            for (int page = 1; page <= totalPages; page++) {
                log.info("Processing page {} of {} using AI...", page, totalPages);
                
                try (PDDocument pageDoc = new PDDocument();
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    
                    pageDoc.addPage(document.getPage(page - 1));
                    pageDoc.save(baos);
                    byte[] pageBytes = baos.toByteArray();

                    UserMessage userMessage = new UserMessage(
                            SMART_EXTRACT_PROMPT,
                            List.of(new Media(MimeType.valueOf("application/pdf"), new ByteArrayResource(pageBytes)))
                    );

                    ChatResponse response = chatModel.call(new Prompt(userMessage));
                    String smartText = response.getResult().getOutput().getContent();

                    if (smartText != null && !smartText.isBlank()) {
                        pageDocuments.add(new Document(smartText, Map.of(
                                "userId", userId,
                                "documentId", documentId,
                                "fileName", file.getOriginalFilename(),
                                "pageNumber", page,
                                "contentType", "application/pdf",
                                "is_cache", false
                        )));
                    }
                }
            }
        }

        if (pageDocuments.isEmpty()) {
            throw new RuntimeException("Could not extract any text from the PDF: " + file.getOriginalFilename());
        }

        TokenTextSplitter splitter = new TokenTextSplitter(800, 400, 5, 1000, true);
        List<Document> chunks = splitter.apply(pageDocuments);
        
        List<Document> documentsWithIds = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            String id = documentId + "_" + i;
            Document chunk = chunks.get(i);
            documentsWithIds.add(new Document(id, chunk.getText(), chunk.getMetadata()));
        }

        vectorStore.add(documentsWithIds);
        firestoreService.saveDocumentMetadata(documentId, userId, file.getOriginalFilename(), file.getSize());
        
        log.info("Successfully ingested {} chunks into ChromaDB with Smart Parsing. DocID: {}", documentsWithIds.size(), documentId);
    }

    public void deleteDocument(String documentId, String userId) {
        log.info("Deleting document {} for user {}", documentId, userId);
        firestoreService.deleteDocumentMetadata(documentId);
    }
}
