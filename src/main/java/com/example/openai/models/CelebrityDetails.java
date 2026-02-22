package com.example.openai.models;

import java.util.ArrayList;
import java.util.List;

public class CelebrityDetails {

    private String name;
    private String profession;
    private String nationality;
    private String birthDate;
    private List<String> knownFor = new ArrayList<>();
    private List<String> notableWorks = new ArrayList<>();
    private List<String> awards = new ArrayList<>();
    private String summary;

    public CelebrityDetails() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfession() {
        return profession;
    }

    public void setProfession(String profession) {
        this.profession = profession;
    }

    public String getNationality() {
        return nationality;
    }

    public void setNationality(String nationality) {
        this.nationality = nationality;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public List<String> getKnownFor() {
        return knownFor;
    }

    public void setKnownFor(List<String> knownFor) {
        this.knownFor = knownFor;
    }

    public List<String> getNotableWorks() {
        return notableWorks;
    }

    public void setNotableWorks(List<String> notableWorks) {
        this.notableWorks = notableWorks;
    }

    public List<String> getAwards() {
        return awards;
    }

    public void setAwards(List<String> awards) {
        this.awards = awards;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
