package com.example.openai.services;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CelebrityTools {

    private static final Map<String, String> BIRTH_YEARS = new HashMap<>();
    private static final Map<String, String> PROFESSIONS = new HashMap<>();

    static {
        BIRTH_YEARS.put("shah rukh khan", "1965");
        BIRTH_YEARS.put("tom cruise", "1962");
        BIRTH_YEARS.put("taylor swift", "1989");
        BIRTH_YEARS.put("virat kohli", "1988");

        PROFESSIONS.put("shah rukh khan", "Actor, Film Producer");
        PROFESSIONS.put("tom cruise", "Actor, Producer");
        PROFESSIONS.put("taylor swift", "Singer-Songwriter");
        PROFESSIONS.put("virat kohli", "Cricketer");
    }

    @Tool(description = "Get the birth year of a celebrity by full name")
    public String getCelebrityBirthYear(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        return BIRTH_YEARS.getOrDefault(name.trim().toLowerCase(), "Unknown");
    }

    @Tool(description = "Get the profession of a celebrity by full name")
    public String getCelebrityProfession(String name) {
        if (name == null || name.isBlank()) {
            return "Unknown";
        }
        return PROFESSIONS.getOrDefault(name.trim().toLowerCase(), "Unknown");
    }
}
