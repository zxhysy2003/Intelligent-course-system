package com.sy.course_system.vo;

public class CourseDetailVO {
    private Long id;
    private String title;
    private String description;
    private Integer difficulty;

    public Long getId() {
        return id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public Integer getDifficulty() {
        return difficulty;
    }
    public void setDifficulty(Integer difficulty) {
        this.difficulty = difficulty;
    }

    


}
