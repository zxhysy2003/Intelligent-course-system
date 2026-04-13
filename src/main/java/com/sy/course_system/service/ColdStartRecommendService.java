package com.sy.course_system.service;

import java.util.List;

import com.sy.course_system.vo.ColdStartRecommendItemVO;

public interface ColdStartRecommendService {

    List<ColdStartRecommendItemVO> recommend(Long userId, Integer limit);
}
