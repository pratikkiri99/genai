package com.example.openai.models;

import java.util.ArrayList;
import java.util.List;

public class RagLoadResponse {

    private int loadedFiles;
    private int loadedChunks;
    private List<String> sources = new ArrayList<>();

    public RagLoadResponse() {
    }

    public RagLoadResponse(int loadedFiles, int loadedChunks, List<String> sources) {
        this.loadedFiles = loadedFiles;
        this.loadedChunks = loadedChunks;
        this.sources = sources;
    }

    public int getLoadedFiles() {
        return loadedFiles;
    }

    public void setLoadedFiles(int loadedFiles) {
        this.loadedFiles = loadedFiles;
    }

    public int getLoadedChunks() {
        return loadedChunks;
    }

    public void setLoadedChunks(int loadedChunks) {
        this.loadedChunks = loadedChunks;
    }

    public List<String> getSources() {
        return sources;
    }

    public void setSources(List<String> sources) {
        this.sources = sources;
    }
}
