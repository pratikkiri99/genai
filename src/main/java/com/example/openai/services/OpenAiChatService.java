package com.example.openai.services;

import com.example.openai.models.CelebrityDetails;
import com.example.openai.models.TemplateChatResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;

@Service
public class OpenAiChatService {

    private ChatClient chatClient;
    private EmbeddingModel embeddingModel;
    private ImageModel imageModel;
    private JdbcTemplate jdbcTemplate;
    private ObjectMapper objectMapper;
    private CelebrityTools celebrityTools;

    private static final String CELEBRITY_DETAILS_SCHEMA = """
                    {
                        "$schema": "https://json-schema.org/draft/2020-12/schema",
                        "type": "object",
                        "additionalProperties": false,
                        "required": [
                            "name",
                            "profession",
                            "nationality",
                            "birthDate",
                            "knownFor",
                            "notableWorks",
                            "awards",
                            "summary"
                        ],
                        "properties": {
                            "name": {"type": "string"},
                            "profession": {"type": "string"},
                            "nationality": {"type": "string"},
                            "birthDate": {"type": "string"},
                            "knownFor": {
                                "type": "array",
                                "items": {"type": "string"},
                                "minItems": 1,
                                "maxItems": 5
                            },
                            "notableWorks": {
                                "type": "array",
                                "items": {"type": "string"},
                                "minItems": 1,
                                "maxItems": 6
                            },
                            "awards": {
                                "type": "array",
                                "items": {"type": "string"},
                                "minItems": 0,
                                "maxItems": 6
                            },
                            "summary": {"type": "string", "minLength": 20}
                        }
                    }
                    """;

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
                              JdbcTemplate jdbcTemplate,
                              ObjectMapper objectMapper,
                              CelebrityTools celebrityTools) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModel;
        this.imageModel = imageModel;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.celebrityTools = celebrityTools;
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

    public TemplateChatResponse chatWithTemplate(String topic, String audience, String tone) {
        String safeTopic = topic == null || topic.isBlank() ? "the topic" : topic;
        String safeAudience = audience == null || audience.isBlank() ? "general audience" : audience;
        String safeTone = tone == null || tone.isBlank() ? "clear" : tone;

        Map<String, Object> variables = Map.of(
            "topic", safeTopic,
            "audience", safeAudience,
            "tone", safeTone
        );

        String systemMessage = """
            You are a helpful technical assistant.
            Return only valid JSON with these keys exactly:
            topic (string), audience (string), tone (string),
            bulletPoints (array of 5 strings), practicalExample (string).
            Do not add markdown, code fences, or extra keys.
            """;

        PromptTemplate userTemplate = new PromptTemplate("""
                Explain {topic} for {audience} in a {tone} tone.
                Generate exactly 5 concise bullet points and one practical example.
            """);

        TemplateChatResponse response = chatClient
                .prompt()
                .system(systemMessage)
                .user(userTemplate.render(variables))
                .call()
                .entity(TemplateChatResponse.class);

        if (response == null) {
            TemplateChatResponse fallback = new TemplateChatResponse();
            fallback.setTopic(safeTopic);
            fallback.setAudience(safeAudience);
            fallback.setTone(safeTone);
            fallback.setBulletPoints(new ArrayList<>());
            fallback.setPracticalExample("Model returned empty structured output.");
            return fallback;
        }

        if (response.getTopic() == null || response.getTopic().isBlank()) {
            response.setTopic(safeTopic);
        }
        if (response.getAudience() == null || response.getAudience().isBlank()) {
            response.setAudience(safeAudience);
        }
        if (response.getTone() == null || response.getTone().isBlank()) {
            response.setTone(safeTone);
        }
        if (response.getBulletPoints() == null) {
            response.setBulletPoints(new ArrayList<>());
        }
        if (response.getPracticalExample() == null || response.getPracticalExample().isBlank()) {
            response.setPracticalExample("No practical example returned.");
        }

        return response;
    }

    public Flux<String> chatCompletionStream(String message) {
        return chatClient
                .prompt()
                .user(message)
                .stream()
                .content();
    }

        public String chatWithTools(String message) {
        return chatClient
            .prompt()
            .system("""
                You can call tools to answer celebrity-related questions.
                Use tools when the user asks for profession or birth year.
                If tool returns Unknown, clearly say data is unavailable.
                """)
            .user(message)
            .tools(celebrityTools)
            .call()
            .content();
        }

    public CelebrityDetails celebrityDetails(String name) {
        String safeName = name == null || name.isBlank() ? "Unknown celebrity" : name.trim();

        String systemMessage = """
                You are a factual assistant.
                Return ONLY valid JSON. Do not include markdown or code fences.
                JSON must strictly match this schema:
                """ + CELEBRITY_DETAILS_SCHEMA;

        String userMessage = "Provide concise biographical details for: " + safeName;

        String llmJson = chatClient
                .prompt()
                .system(systemMessage)
                .user(userMessage)
                .call()
                .content();

        if (llmJson == null || llmJson.isBlank()) {
            throw new IllegalArgumentException("Model returned empty content.");
        }

        try {
            JsonNode outputNode = objectMapper.readTree(llmJson);
            validateCelebritySchema(outputNode);
            CelebrityDetails details = objectMapper.treeToValue(outputNode, CelebrityDetails.class);
            if (details.getName() == null || details.getName().isBlank()) {
                details.setName(safeName);
            }
            return details;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Model response is not valid JSON.", e);
        }
    }

    private void validateCelebritySchema(JsonNode outputNode) throws JsonProcessingException {
        JsonNode schemaNode = objectMapper.readTree(CELEBRITY_DETAILS_SCHEMA);

        if (!outputNode.isObject()) {
            throw new IllegalArgumentException("LLM output failed JSON schema validation: expected JSON object.");
        }

        JsonNode required = schemaNode.path("required");
        if (required.isArray()) {
            for (JsonNode requiredField : required) {
                String fieldName = requiredField.asText();
                if (!outputNode.has(fieldName) || outputNode.get(fieldName).isNull()) {
                    throw new IllegalArgumentException("LLM output failed JSON schema validation: missing required field '" + fieldName + "'.");
                }
            }
        }

        JsonNode properties = schemaNode.path("properties");
        if (!properties.isObject()) {
            throw new IllegalArgumentException("LLM output failed JSON schema validation: invalid schema properties.");
        }

        if (schemaNode.path("additionalProperties").isBoolean() && !schemaNode.path("additionalProperties").asBoolean()) {
            HashSet<String> allowedFields = new HashSet<>();
            properties.fieldNames().forEachRemaining(allowedFields::add);
            outputNode.fieldNames().forEachRemaining(field -> {
                if (!allowedFields.contains(field)) {
                    throw new IllegalArgumentException("LLM output failed JSON schema validation: additional field '" + field + "' is not allowed.");
                }
            });
        }

        validateCelebrityField(outputNode, properties, "name");
        validateCelebrityField(outputNode, properties, "profession");
        validateCelebrityField(outputNode, properties, "nationality");
        validateCelebrityField(outputNode, properties, "birthDate");
        validateCelebrityField(outputNode, properties, "knownFor");
        validateCelebrityField(outputNode, properties, "notableWorks");
        validateCelebrityField(outputNode, properties, "awards");
        validateCelebrityField(outputNode, properties, "summary");
    }

    private void validateCelebrityField(JsonNode outputNode, JsonNode properties, String fieldName) {
        JsonNode schemaField = properties.path(fieldName);
        JsonNode valueNode = outputNode.path(fieldName);
        String expectedType = schemaField.path("type").asText();

        if ("string".equals(expectedType)) {
            if (!valueNode.isTextual()) {
                throw new IllegalArgumentException("LLM output failed JSON schema validation: field '" + fieldName + "' must be string.");
            }
            int minLength = schemaField.path("minLength").asInt(0);
            if (valueNode.asText().trim().length() < minLength) {
                throw new IllegalArgumentException("LLM output failed JSON schema validation: field '" + fieldName + "' is shorter than minLength.");
            }
            return;
        }

        if ("array".equals(expectedType)) {
            if (!valueNode.isArray()) {
                throw new IllegalArgumentException("LLM output failed JSON schema validation: field '" + fieldName + "' must be array.");
            }
            int size = valueNode.size();
            int minItems = schemaField.path("minItems").asInt(0);
            int maxItems = schemaField.path("maxItems").asInt(Integer.MAX_VALUE);
            if (size < minItems || size > maxItems) {
                throw new IllegalArgumentException("LLM output failed JSON schema validation: field '" + fieldName + "' item count out of range.");
            }
            for (JsonNode item : valueNode) {
                if (!item.isTextual()) {
                    throw new IllegalArgumentException("LLM output failed JSON schema validation: all items in '" + fieldName + "' must be strings.");
                }
            }
        }
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
