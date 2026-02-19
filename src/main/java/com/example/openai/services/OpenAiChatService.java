package com.example.openai.services;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
public class OpenAiChatService {

    private ChatClient chatClient;
    private EmbeddingModel embeddingModel;
    private ImageModel imageModel;
    private JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.openai.api-key:}")
    private String openAiApiKey;

    @Value("${spring.ai.openai.audio.speech.options.model:gpt-4o-mini-tts}")
    private String ttsModel;

    @Value("${spring.ai.openai.audio.speech.options.voice:alloy}")
    private String ttsVoice;

    @Value("${spring.ai.openai.audio.speech.options.response-format:mp3}")
    private String ttsResponseFormat;



    public OpenAiChatService (ChatClient.Builder chatClientBuilder,
                              EmbeddingModel embeddingModel,
                              ImageModel imageModel,
                              JdbcTemplate jdbcTemplate) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.imageModel = imageModel;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Chat completion example
     * @param message
     * @return
     */
    public String chatCompletion(String message) {
        var response = chatClient
                .prompt()
                .user(message)
                .call()
                .chatResponse();

        if (response == null ||
                response.getResult() == null ||
                response.getResult().getOutput() == null) {
            return "No output returned by model.";
        }
        var content = response.getResult().getOutput().getText();
        if (content == null || content.isBlank()) {
            return "Model returned empty content.";
        }
        return content;
    }

    public float[] saveDocumentWithEmbedding(String content) {
        float[] embedding = embeddingModel.embed(content);
        // Convert float[] to PostgreSQL vector literal: '[1.0,2.0,...]'
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        String embeddingLiteral = sb.toString();

        String sql = "INSERT INTO documents (content, embedding) VALUES (?, ?::vector)";
        jdbcTemplate.update(sql, content, embeddingLiteral);
        return embedding;
    }

    public String generateImage(String prompt) {
        ImageResponse response = imageModel.call(
                new ImagePrompt(
                        prompt,
                        OpenAiImageOptions.builder()
                    .model("gpt-image-1")
                                .build()
                )
        );

        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "No image returned by model.";
        }
        String url = response.getResult().getOutput().getUrl();
        String b64Json = response.getResult().getOutput().getB64Json();
        if ((url == null || url.isBlank()) && (b64Json == null || b64Json.isBlank())) {
            return "Image URL or base64 data not returned by model.";
        }

        String safeName = toSafeFilename(prompt);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = safeName + "_" + timestamp + ".png";
        Path outputDir = Paths.get("generated-images");
        Path outputPath = outputDir.resolve(fileName);

        try {
            Files.createDirectories(outputDir);
            if (url != null && !url.isBlank()) {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
                HttpResponse<byte[]> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                Files.write(outputPath, httpResponse.body());
            } else {
                byte[] imageBytes = Base64.getDecoder().decode(b64Json);
                Files.write(outputPath, imageBytes);
            }
            return "Saved image to: " + outputPath.toAbsolutePath();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Failed to save image: " + e.getMessage();
        } catch (IOException e) {
            return "Failed to save image: " + e.getMessage();
        }
    }

    public String generateSpeech(String prompt) {
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return "OPENAI_KEY is not set.";
        }

        String requestBody = "{" +
                "\"model\":\"" + jsonEscape(ttsModel) + "\"," +
                "\"input\":\"" + jsonEscape(prompt) + "\"," +
                "\"voice\":\"" + jsonEscape(ttsVoice) + "\"," +
                "\"response_format\":\"" + jsonEscape(ttsResponseFormat) + "\"" +
                "}";

        String safeName = toSafeFilename(prompt);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = safeName + "_" + timestamp + ".mp3";
        Path outputDir = Paths.get("generated-audio");
        Path outputPath = outputDir.resolve(fileName);

        try {
            Files.createDirectories(outputDir);
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/audio/speech"))
                    .header("Authorization", "Bearer " + openAiApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<byte[]> httpResponse = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() >= 400) {
                return "Failed to generate audio: HTTP " + httpResponse.statusCode();
            }
            Files.write(outputPath, httpResponse.body());
            return "Saved audio to: " + outputPath.toAbsolutePath();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Failed to save audio: " + e.getMessage();
        }
    }

    private String toSafeFilename(String prompt) {
        String trimmed = prompt == null ? "image" : prompt.trim();
        if (trimmed.isEmpty()) {
            return "image";
        }
        String safe = trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.length() > 60) {
            safe = safe.substring(0, 60);
        }
        return safe;
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }


}
