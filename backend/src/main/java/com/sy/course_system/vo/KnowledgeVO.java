package com.sy.course_system.vo;

/**
 * 轻量知识点视图。
 *
 * 该类型现在同时服务于：
 * 1) 通用课程接口的知识点展示；
 * 2) 推荐链路中的知识点概览与学习路径展示。
 *
 * 之所以复用这一份结构，是因为这两个场景对外暴露的字段完全一致，
 * 都只需要 id / name / difficulty，继续维护一份平行 DTO 只会增加冗余。
 */
public class KnowledgeVO {
    private Long id;
    private String name;
    private Integer difficulty; // 1-4

    public KnowledgeVO() {
    }

    public KnowledgeVO(Long id, String name, Integer difficulty) {
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
