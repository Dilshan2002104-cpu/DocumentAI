package com.enterprise.document_ai.controller;

import com.enterprise.document_ai.service.DocumentIngestionService;
import com.enterprise.document_ai.service.FirestoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentIngestionService ingestionService;
    private final FirestoreService firestoreService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal String firebaseUid) {
        
        log.info("Received upload request for: {} from user: {}", file.getOriginalFilename(), firebaseUid);

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please upload a valid PDF file.");
        }

        try {
            ingestionService.ingestPdf(file, firebaseUid);
            return ResponseEntity.ok("File processed and added to knowledge base successfully.");
        } catch (Exception e) {
            log.error("Failed to process document: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Error processing document: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listDocuments(
            @AuthenticationPrincipal String firebaseUid) {
        try {
            return ResponseEntity.ok(firestoreService.getUserDocuments(firebaseUid));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteDocument(
            @PathVariable("id") String documentId,
            @AuthenticationPrincipal String firebaseUid) {
        
        try {
            ingestionService.deleteDocument(documentId, firebaseUid);
            return ResponseEntity.ok("Document deleted successfully.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error: " + e.getMessage());
        }
    }
}
