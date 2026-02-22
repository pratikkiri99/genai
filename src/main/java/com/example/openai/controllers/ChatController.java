package com.example.openai.controllers;

import com.example.openai.models.CelebrityDetails;
import com.example.openai.models.TemplateChatResponse;
import com.example.openai.services.OpenAiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    @Autowired
    OpenAiChatService openAiChatService;

    @PostMapping("/ask")
    public String chatComplete(@RequestParam(name = "request") String message) {
        return openAiChatService.chatCompletion(message);
    }

    @PostMapping("/ask/template")
    public TemplateChatResponse chatTemplate(@RequestParam(name = "topic") String topic,
                                             @RequestParam(name = "audience") String audience,
                                             @RequestParam(name = "tone") String tone) {
        return openAiChatService.chatWithTemplate(topic, audience, tone);
    }

    @PostMapping("/ask/celebrity")
    public CelebrityDetails celebrityDetails(@RequestParam(name = "name") String name) {
        return openAiChatService.celebrityDetails(name);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatCompleteStream(@RequestParam(name = "request") String message) {
        return openAiChatService.chatCompletionStream(message);
    }

    @PostMapping("/ask/tools")
    public String chatWithTools(@RequestParam(name = "request") String message) {
        return openAiChatService.chatWithTools(message);
    }

    @PostMapping("/embed")
    public float[] embed(@RequestParam(name = "request") String message) {
        return openAiChatService.saveDocumentWithEmbedding(message);
    }

    @PostMapping("/image")
    public String image(@RequestParam(name = "prompt") String prompt) {
        return openAiChatService.generateImage(prompt);
    }

    @PostMapping("/speech")
    public String speech(@RequestParam(name = "prompt") String prompt) {
        return openAiChatService.generateSpeech(prompt);
    }


}
