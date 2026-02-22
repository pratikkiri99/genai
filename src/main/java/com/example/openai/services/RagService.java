package com.example.openai.services;

import com.example.openai.models.RagAnswerResponse;
import com.example.openai.models.RagLoadResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Jsoup;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

@Service
public class RagService {

    private static final int CHUNK_SIZE = 1200;
    private static final int CHUNK_OVERLAP = 200;
    private static final String DELETE_ALL_CHUNKS_SQL = "DELETE FROM rag_chunks";
    private static final String INSERT_CHUNK_SQL = "INSERT INTO rag_chunks (source, chunk_index, content, embedding) VALUES (?, ?, ?, ?::vector)";
    private static final String SELECT_TOP_CHUNKS_SQL = """
            SELECT source, chunk_index, content
            FROM rag_chunks
            ORDER BY embedding <=> ?::vector
            LIMIT ?
            """;
    private static final String COUNT_CHUNKS_SQL = "SELECT COUNT(*) FROM rag_chunks";

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final JdbcTemplate jdbcTemplate;

    public RagService(ChatClient.Builder chatClientBuilder, EmbeddingModel embeddingModel, JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    public synchronized RagLoadResponse loadDocuments(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("Folder path is required.");
        }

        String normalizedFolderPath = normalizePathInput(folderPath);

        Path inputPath;
        try {
            inputPath = Path.of(normalizedFolderPath);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Invalid folder path: " + folderPath, e);
        }

        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Invalid folder path: " + normalizedFolderPath);
        }

        List<String> loadedSources = new ArrayList<>();
        List<DocumentChunk> newChunks = new ArrayList<>();

        try {
            if (Files.isDirectory(inputPath)) {
                try (Stream<Path> paths = Files.walk(inputPath)) {
                    paths.filter(Files::isRegularFile)
                            .filter(this::isSupported)
                            .forEach(file -> loadSingleFile(file, loadedSources, newChunks));
                }
            } else if (Files.isRegularFile(inputPath) && isSupported(inputPath)) {
                loadSingleFile(inputPath, loadedSources, newChunks);
            } else {
                throw new IllegalArgumentException("Unsupported file type: " + inputPath);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read documents: " + e.getMessage(), e);
        }

        replaceChunksInDatabase(newChunks);

        return new RagLoadResponse(loadedSources.size(), newChunks.size(), loadedSources);
    }

    private void replaceChunksInDatabase(List<DocumentChunk> chunks) {
        jdbcTemplate.update(DELETE_ALL_CHUNKS_SQL);
        if (chunks.isEmpty()) {
            return;
        }

        jdbcTemplate.batchUpdate(
                INSERT_CHUNK_SQL,
                chunks,
                200,
                (ps, chunk) -> {
                    ps.setString(1, chunk.source());
                    ps.setInt(2, chunk.chunkIndex());
                    ps.setString(3, chunk.text());
                    ps.setString(4, toVectorLiteral(chunk.embedding()));
                }
        );
    }

    private void loadSingleFile(Path file, List<String> loadedSources, List<DocumentChunk> newChunks) {
        String text = extractText(file);
        if (text == null || text.isBlank()) {
            return;
        }

        List<String> chunks = chunkText(text);
        int index = 0;
        for (String chunkText : chunks) {
            float[] embedding = embeddingModel.embed(chunkText);
            newChunks.add(new DocumentChunk(file.getFileName().toString(), index++, chunkText, embedding));
        }
        loadedSources.add(file.toAbsolutePath().toString());
    }

    private String normalizePathInput(String pathInput) {
        String trimmed = pathInput.trim();
        if (trimmed.length() >= 2) {
            boolean wrappedInDoubleQuotes = trimmed.startsWith("\"") && trimmed.endsWith("\"");
            boolean wrappedInSingleQuotes = trimmed.startsWith("'") && trimmed.endsWith("'");
            if (wrappedInDoubleQuotes || wrappedInSingleQuotes) {
                return trimmed.substring(1, trimmed.length() - 1).trim();
            }
        }
        return trimmed;
    }

    public synchronized RagAnswerResponse ask(String question, int topK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question is required.");
        }
        Integer chunkCount = jdbcTemplate.queryForObject(COUNT_CHUNKS_SQL, Integer.class);
        if (chunkCount == null || chunkCount == 0) {
            throw new IllegalArgumentException("No documents loaded. Load documents first using /rag/load.");
        }

        int safeTopK = Math.max(1, Math.min(topK, 8));
        float[] queryEmbedding = embeddingModel.embed(question);
        String queryEmbeddingLiteral = toVectorLiteral(queryEmbedding);

        List<DocumentChunk> matchedChunks = jdbcTemplate.query(
            SELECT_TOP_CHUNKS_SQL,
            (rs, rowNum) -> new DocumentChunk(
                rs.getString("source"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                null
            ),
            queryEmbeddingLiteral,
            safeTopK
        );

        StringBuilder contextBuilder = new StringBuilder();
        LinkedHashSet<String> sourceSet = new LinkedHashSet<>();

        for (DocumentChunk chunk : matchedChunks) {
            sourceSet.add(chunk.source());
            contextBuilder.append("Source: ")
                    .append(chunk.source())
                    .append(" | Chunk: ")
                    .append(chunk.chunkIndex())
                    .append("\n")
                    .append(chunk.text())
                    .append("\n\n");
        }

        String answer = chatClient
                .prompt()
                .system("""
                        You are a RAG assistant. Use only the provided context to answer.
                        If answer is not in context, say you don't have enough context.
                        Keep answer concise and include source names at the end.
                        """)
                .user("""
                        Question:
                        %s

                        Context:
                        %s
                        """.formatted(question, contextBuilder))
                .call()
                .content();

        return new RagAnswerResponse(answer, new ArrayList<>(sourceSet), matchedChunks.size());
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            builder.append(embedding[i]);
            if (i < embedding.length - 1) {
                builder.append(',');
            }
        }
        builder.append(']');
        return builder.toString();
    }

    private boolean isSupported(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".pdf")
                || fileName.endsWith(".txt")
                || fileName.endsWith(".md")
                || fileName.endsWith(".html")
                || fileName.endsWith(".htm");
    }

    private String extractText(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);

        try {
            if (fileName.endsWith(".pdf")) {
                return extractPdfText(file);
            }
            if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
                String html = Files.readString(file, StandardCharsets.UTF_8);
                return Jsoup.parse(html).text();
            }
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String extractPdfText(Path file) throws IOException {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    private List<String> chunkText(String text) {
        String normalized = text.replaceAll("\\s+", " ").trim();
        List<String> chunks = new ArrayList<>();

        if (normalized.isBlank()) {
            return chunks;
        }

        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(0, end - CHUNK_OVERLAP);
        }
        return chunks;
    }

    private record DocumentChunk(String source, int chunkIndex, String text, float[] embedding) {
    }
}
