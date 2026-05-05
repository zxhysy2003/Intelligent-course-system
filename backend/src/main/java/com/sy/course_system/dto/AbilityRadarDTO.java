package com.sy.course_system.dto;

import java.util.List;

public class AbilityRadarDTO {
    private List<RadarIndicatorDTO> indicator;
    private List<Double> values;

    public List<RadarIndicatorDTO> getIndicator() {
        return indicator;
    }

    public void setIndicator(List<RadarIndicatorDTO> indicator) {
        this.indicator = indicator;
    }

    public List<Double> getValues() {
        return values;
    }

    public void setValues(List<Double> values) {
        this.values = values;
    }
}
