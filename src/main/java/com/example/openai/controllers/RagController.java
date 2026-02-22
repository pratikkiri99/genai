package com.example.openai.controllers;

import com.example.openai.models.RagAnswerResponse;
import com.example.openai.models.RagLoadResponse;
import com.example.openai.services.RagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RagController {

    @Autowired
    private RagService ragService;

    @PostMapping("/rag/load")
    public RagLoadResponse load(@RequestParam(name = "path") String path) {
        return ragService.loadDocuments(path);
    }

    @PostMapping("/rag/ask")
    public RagAnswerResponse ask(@RequestParam(name = "question") String question,
                                 @RequestParam(name = "topK", defaultValue = "4") int topK) {
        return ragService.ask(question, topK);
    }
}
