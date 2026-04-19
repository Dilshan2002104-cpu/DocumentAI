# Enterprise PDF RAG System

An advanced, production-ready Retrieval-Augmented Generation (RAG) system for chatting with PDF documents. Built with **Spring Boot 3.4.2**, **Java 21**, and **Google Vertex AI Gemini**.

## 🚀 Key Features

- **Multimodal Intelligence**: Uses Gemini Vision to extract text, tables (as Markdown), and images from PDF pages for higher accuracy.
- **Precise Citations**: Automatically tracks filenames and page numbers, instructing the AI to cite sources in every answer.
- **Hybrid Search**: Combines semantic vector search with keyword-based expansion for the most relevant context retrieval.
- **Semantic Caching**: Caches similar questions and answers in ChromaDB to reduce costs and latency.
- **Real-time Streaming**: Leverages Firebase Firestore to stream AI responses to the UI in real-time.
- **Enterprise Security**: Stateless JWT authentication using Firebase Admin SDK.
- **Audit Logs & Analytics**: Tracks every interaction, query, and retrieved source for compliance and monitoring.

## 🛠️ Technology Stack

- **Backend**: Spring Boot 3.4.2, Spring AI (1.0.0-M5)
- **AI Model**: Google Vertex AI Gemini 2.0 Flash
- **Vector Database**: ChromaDB (v0.4.24)
- **Authentication**: Firebase Auth
- **Real-time DB**: Firebase Firestore (for chat streaming & metadata)
- **PDF Extraction**: Apache PDFBox + Gemini Multimodal Parsing

## ⚙️ Getting Started

### Prerequisites
- Docker (for ChromaDB)
- Java 21
- Google Cloud Service Account (Vertex AI access)
- Firebase Service Account Key

### Setup
1. **Infrastructure**:
   ```bash
   docker-compose up -d
   ```

2. **Configuration**:
   Add your `firebase-service-account.json` to the root and update `src/main/resources/application.properties` with your Google Cloud project details.

3. **Run Application**:
   ```bash
   cd document-ai
   ./mvnw spring-boot:run
   ```

## 📡 API Endpoints

- `POST /api/v1/documents/upload`: Upload PDF files.
- `GET /api/v1/documents`: List uploaded files.
- `DELETE /api/v1/documents/{id}`: remove indexed data.
- `POST /api/v1/chat`: Initiate a RAG chat session (returns `chatId`).

## 📜 License
MIT
