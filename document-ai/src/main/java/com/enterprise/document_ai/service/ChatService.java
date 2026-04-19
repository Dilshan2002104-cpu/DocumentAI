package com.enterprise.document_ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vertexai.gemini.VertexAiGeminiChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final VertexAiGeminiChatModel chatModel;
    private final VectorStore vectorStore;
    private final FirestoreService firestoreService;

    private static final String SYSTEM_PROMPT = """
            You are a helpful Enterprise AI assistant. 
            Use the following context from uploaded PDF documents to answer the user's question.
            If you don't know the answer based on the context, just say you don't know. 
            Do not make up information.
            
            CONTEXT:
            {context}
            """;

    /**
     * Retrieves context, calls Gemini, and streams the response to Firestore.
     * @param query The user's question
     * @param userId To filter documents belonging to this user
     * @return A unique chatId that the frontend can listen to
     */
    public String chatWithDocuments(String query, String userId) {
        String chatId = UUID.randomUUID().toString();
        log.info("Starting Chat RAG session: {} for user: {}", chatId, userId);

        // 1. Retrieval: Find top 4 relevant chunks for this user
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(4)
                .filterExpression("userId == '" + userId + "'")
                .build();
        
        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
        
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 2. Augmentation: Build the prompt
        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
        org.springframework.ai.chat.messages.Message systemMessage = systemPromptTemplate.createMessage(Map.of("context", context));
        UserMessage userMessage = new UserMessage(query);
        
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        // 3. Generation & Streaming:
        // Use Flux to handle the stream and write to Firestore
        StringBuilder fullResponse = new StringBuilder();
        
        chatModel.stream(prompt).subscribe(
            response -> {
                String chunk = response.getResult().getOutput().getContent();
                if (chunk != null) {
                    fullResponse.append(chunk);
                    // Update Firestore with the current text
                    firestoreService.updateChatStatus(chatId, fullResponse.toString(), false);
                }
            },
            error -> {
                log.error("Streaming error for chatId {}: {}", chatId, error.getMessage());
                firestoreService.updateChatStatus(chatId, "Error: AI failed to generate response.", true);
            },
            () -> {
                log.info("Finished streaming for chatId: {}", chatId);
                // Mark as complete
                firestoreService.updateChatStatus(chatId, fullResponse.toString(), true);
            }
        );

        return chatId;
    }
}
