package com.example.openai.models;

import java.util.ArrayList;
import java.util.List;

public class RagAnswerResponse {

    private String answer;
    private List<String> sources = new ArrayList<>();
    private int matchedChunks;

    public RagAnswerResponse() {
    }

    public RagAnswerResponse(String answer, List<String> sources, int matchedChunks) {
        this.answer = answer;
        this.sources = sources;
        this.matchedChunks = matchedChunks;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }

    public int getMatchedChunks() {
        return matchedChunks;
    }

    public void setMatchedChunks(int matchedChunks) {
        this.matchedChunks = matchedChunks;
    }
}
