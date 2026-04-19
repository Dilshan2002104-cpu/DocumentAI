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

@Service
@Slf4j
public class FirestoreService {

    public void updateChatStatus(String chatId, String content, boolean isComplete) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            
            Map<String, Object> data = new HashMap<>();
            data.put("content", content);
            data.put("isComplete", isComplete);
            data.put("updatedAt", System.currentTimeMillis());

            db.collection("chats").document(chatId).set(data);
            
        } catch (Exception e) {
            log.error("Failed to update Firestore for chatId {}: {}", chatId, e.getMessage());
        }
    }

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

    public void deleteDocumentMetadata(String documentId) {
        FirestoreClient.getFirestore().collection("documents").document(documentId).delete();
    }

    public void saveAuditLog(String userId, String chatId, String query, String response, List<String> sources) {
        try {
            Firestore db = FirestoreClient.getFirestore();
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("userId", userId);
            logEntry.put("chatId", chatId);
            logEntry.put("query", query);
            logEntry.put("response", response);
            logEntry.put("sources", sources);
            logEntry.put("timestamp", System.currentTimeMillis());

            db.collection("audit_logs").document(chatId).set(logEntry);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage());
        }
    }
}
