package com.enterprise.document_ai.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Service to handle real-time status and message updates to Firebase Firestore.
 * This allows the frontend to listen to a specific document for streaming AI responses.
 */
@Service
@Slf4j
public class FirestoreService {

    /**
     * Creates or updates a chat message document in Firestore.
     * @param chatId Unique ID for the chat session
     * @param content The current accumulated or partial content
     * @param isComplete Whether the AI has finished generating
     */
    public void updateChatStatus(String chatId, String content, boolean isComplete) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            
            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("isComplete", isComplete);
            data.put("updatedAt", System.currentTimeMillis());

            // Write to the "chats" collection
            db.collection("chats").document(chatId).set(data);
            
        } catch (Exception e) {
            log.error("Failed to update Firestore for chatId {}: {}", chatId, e.getMessage());
        }
    }

    /**
     * Store metadata about uploaded documents.
     */
    public void saveDocumentMetadata(String documentId, String userId, String fileName, long size) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            Map<String, Object> data = new HashMap<>();
            data.put("id", documentId);
            data.put("userId", userId);
            data.put("fileName", fileName);
            data.put("size", size);
            data.put("uploadedAt", System.currentTimeMillis());

            db.collection("documents").document(documentId).set(data);
        } catch (Exception e) {
            log.error("Failed to save metadata for document: {}", fileName);
        }
    }

    /**
     * List all documents for a specific user.
     */
    public List<Map<String, Object>> getUserDocuments(String userId) throws ExecutionException, InterruptedException {
        Firestore db = FirestoreClient.getFirestore();
        ApiFuture<QuerySnapshot> future = db.collection("documents")
                .whereEqualTo("userId", userId)
                .get();
        
        List<QueryDocumentSnapshot> docs = future.get().getDocuments();
        List<Map<String, Object>> result = new ArrayList<>();
        for (QueryDocumentSnapshot doc : docs) {
            result.add(doc.getData());
        }
        return result;
    }

    /**
     * Remove metadata.
     */
    public void deleteDocumentMetadata(String documentId) {
        FirestoreClient.getFirestore().collection("documents").document(documentId).delete();
    }
}
