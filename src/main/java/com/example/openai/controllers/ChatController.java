package com.example.openai.controllers;

import com.example.openai.services.OpenAiChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

    @Autowired
    OpenAiChatService openAiChatService;

    @PostMapping("/ask")
    public String chatComplete(@RequestParam(name = "request") String message) {
        return openAiChatService.chatCompletion(message);
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
