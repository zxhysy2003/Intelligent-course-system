package com.sy.course_system.dto;

public class RadarIndicatorDTO {
    private String name;
    private Integer max;

    public RadarIndicatorDTO() {
    }

    public RadarIndicatorDTO(String name, Integer max) {
        this.name = name;
        this.max = max;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }
}
