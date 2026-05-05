package com.sy.course_system.vo;

/**
 * 推荐页展示用的先修掌握度信息。
 *
 * 该结构只保留前端真正需要的“还差多少”展示字段，
 * 同时也被推荐链路内部复用，避免再维护一份字段完全一致的平行 DTO。
 */
public class KnowledgeMasteryVO {
    private Long id;
    private String name;
    private Integer difficulty;
    private Double have;
    private Double need;

    public KnowledgeMasteryVO() {
    }

    public KnowledgeMasteryVO(Long id, String name, Integer difficulty, Double have, Double need) {
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
