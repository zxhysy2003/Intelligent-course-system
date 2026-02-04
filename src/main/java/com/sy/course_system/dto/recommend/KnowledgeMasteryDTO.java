package com.sy.course_system.dto.recommend;

public class KnowledgeMasteryDTO {
    private Long id;
    private String name;
    private Integer difficulty;
    private Double have;
    private Double need;
    
    public KnowledgeMasteryDTO() {
    }

    public KnowledgeMasteryDTO(Long id, String name, Integer difficulty, Double have, Double need) {
        this.id = id;
        this.name = name;
        this.difficulty = difficulty;
        this.have = have;
        this.need = need;
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

    public Double getHave() {
        return have;
    }

    public void setHave(Double have) {
        this.have = have;
    }

    public Double getNeed() {
        return need;
    }

    public void setNeed(Double need) {
        this.need = need;
    }

    
}
