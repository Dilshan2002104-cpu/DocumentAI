package com.enterprise.document_ai.controller;

import com.enterprise.document_ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<Map<String, String>> startChat(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal String firebaseUid) {
        
        String query = request.get("query");
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        String chatId = chatService.chatWithDocuments(query, firebaseUid);
        
        return ResponseEntity.ok(Map.of(
                "chatId", chatId,
                "status", "STREAMING_STARTED",
                "listeningUrl", "firestore://chats/" + chatId
        ));
    }
}
