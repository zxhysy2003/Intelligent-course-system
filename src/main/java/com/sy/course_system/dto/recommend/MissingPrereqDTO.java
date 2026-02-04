package com.sy.course_system.dto.recommend;

public class MissingPrereqDTO {
    private Long id;
    private String name;
    private Double have; // 用户已有掌握度
    private Double need; // 阈值
    private Integer difficulty; // 难度等级
 
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
    public Integer getDifficulty() {
        return difficulty;
    }
    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }
}
