package com.enterprise.document_ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
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
            Each piece of context is prefixed with its source file and page number.
            
            IMPORTANT: When you use information from the context, you MUST cite the source 
            at the end of the sentence or paragraph, for example: (Source: doc.pdf, Page: 5).
            
            If you don't know the answer based on the context, just say you don't know. 
            Do not make up information.
            
            CONTEXT:
            {context}
            """;

    public String chatWithDocuments(String query, String userId) {
        String chatId = UUID.randomUUID().toString();
        log.info("Starting Hybrid Chat session with Semantic Cache: {} for user: {}", chatId, userId);

        SearchRequest cacheRequest = SearchRequest.builder()
                .query(query)
                .topK(1)
                .similarityThreshold(0.95)
                .filterExpression("is_cache == true")
                .build();
        
        List<Document> cachedResults = vectorStore.similaritySearch(cacheRequest);
        if (!cachedResults.isEmpty()) {
            log.info("Semantic Cache HIT for query: {}", query);
            String cachedAnswer = cachedResults.get(0).getText();
            firestoreService.updateChatStatus(chatId, cachedAnswer, true);
            return chatId;
        }

        String expansionPrompt = String.format(
                "Generate 2 diverse search queries for a vector database based on this: \"%s\".", query);
        
        ChatResponse expansionResponse = chatModel.call(new Prompt(expansionPrompt));
        String[] expandedQueries = expansionResponse.getResult().getOutput().getContent().split("\n");
        
        List<Document> allRelevantDocs = new java.util.ArrayList<>();
        allRelevantDocs.addAll(performSearch(query, userId));
        
        for (String q : expandedQueries) {
            if (!q.isBlank()) allRelevantDocs.addAll(performSearch(q.trim(), userId));
        }

        List<Document> uniqueDocs = allRelevantDocs.stream()
                .distinct()
                .limit(8)
                .toList();

        String context = uniqueDocs.stream()
                .map(doc -> String.format("[Source: %s, Page: %s]\n%s", 
                        doc.getMetadata().getOrDefault("fileName", "Unknown"),
                        doc.getMetadata().getOrDefault("pageNumber", "N/A"),
                        doc.getText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        SystemPromptTemplate systemPromptTemplate = new SystemPromptTemplate(SYSTEM_PROMPT);
        org.springframework.ai.chat.messages.Message systemMessage = systemPromptTemplate.createMessage(Map.of("context", context));
        UserMessage userMessage = new UserMessage(query);
        
        Prompt prompt = new Prompt(List.of(systemMessage, userMessage));

        StringBuilder fullResponse = new StringBuilder();
        chatModel.stream(prompt).subscribe(
            response -> {
                String chunk = response.getResult().getOutput().getContent();
                if (chunk != null) {
                    fullResponse.append(chunk);
                    firestoreService.updateChatStatus(chatId, fullResponse.toString(), false);
                }
            },
            error -> {
                log.error("Streaming error: {}", error.getMessage());
                firestoreService.updateChatStatus(chatId, "Error generating response.", true);
            },
            () -> {
                String finalResult = fullResponse.toString();
                firestoreService.updateChatStatus(chatId, finalResult, true);
                
                Document cacheDoc = new Document(finalResult, Map.of(
                        "is_cache", true,
                        "original_query", query,
                        "userId", userId
                ));
                vectorStore.add(List.of(cacheDoc));

                List<String> sourceFiles = uniqueDocs.stream()
                        .map(doc -> doc.getMetadata().getOrDefault("fileName", "Unknown").toString())
                        .distinct()
                        .toList();
                firestoreService.saveAuditLog(userId, chatId, query, finalResult, sourceFiles);
                
                log.info("Saved response to Semantic Cache and Audit Logs.");
            }
        );

        return chatId;
    }

    private List<Document> performSearch(String query, String userId) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(3)
                .filterExpression("userId == '" + userId + "' && is_cache == false")
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }
}
