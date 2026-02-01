package com.sy.course_system.dto.recommend;

public class KnowledgeBriefDTO {
    private Long id;
    private String name;
    private Integer difficulty;

    public KnowledgeBriefDTO() {
    }

    public KnowledgeBriefDTO(Long id, String name, Integer difficulty) {
        this.id = id;
        this.name = name;
        this.difficulty = difficulty;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    
}
