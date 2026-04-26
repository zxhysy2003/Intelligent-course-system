package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.converter.HybridRecommendMapperStruct;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.vo.HybridRecommendResponseVO;

@RestController
@RequestMapping("/recommend")
public class RecommendController {

    @Autowired
    private HybridRecommendService hybridRecommendService;
    @Autowired
    private HybridRecommendMapperStruct hybridRecommendMapperStruct;

    @GetMapping("/hybrid")
    public Result<HybridRecommendResponseVO> hybridRecommend() {
        Long userId = UserContext.getUserId();
        return Result.success(hybridRecommendMapperStruct.toResponseVO(hybridRecommendService.recommend(userId)));
    }
}
