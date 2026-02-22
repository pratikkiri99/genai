package com.example.openai.models;

import java.util.ArrayList;
import java.util.List;

public class TemplateChatResponse {

    private String topic;
    private String audience;
    private String tone;
    private List<String> bulletPoints = new ArrayList<>();
    private String practicalExample;

    public TemplateChatResponse() {
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
    }

    public List<String> getBulletPoints() {
        return bulletPoints;
    }

    public void setBulletPoints(List<String> bulletPoints) {
        this.bulletPoints = bulletPoints;
    }

    public String getPracticalExample() {
        return practicalExample;
    }

    public void setPracticalExample(String practicalExample) {
        this.practicalExample = practicalExample;
    }
}
