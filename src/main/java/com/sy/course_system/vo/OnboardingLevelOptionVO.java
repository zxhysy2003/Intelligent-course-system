package com.sy.course_system.vo;

public class OnboardingLevelOptionVO {

    private Integer value;
    private String label;

    public OnboardingLevelOptionVO() {
    }

    public OnboardingLevelOptionVO(Integer value, String label) {
        this.value = value;
        this.label = label;
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
