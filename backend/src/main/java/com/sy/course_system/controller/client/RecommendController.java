package com.sy.course_system.controller.client;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.ApiPaths;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.converter.HybridRecommendMapperStruct;
import com.sy.course_system.service.HybridRecommendService;
import com.sy.course_system.vo.HybridRecommendResponseVO;

@RestController
@RequestMapping(ApiPaths.RECOMMENDATIONS)
public class RecommendController {

    private final HybridRecommendService hybridRecommendService;
    private final HybridRecommendMapperStruct hybridRecommendMapperStruct;

    public RecommendController(HybridRecommendService hybridRecommendService,
            HybridRecommendMapperStruct hybridRecommendMapperStruct) {
        this.hybridRecommendService = hybridRecommendService;
        this.hybridRecommendMapperStruct = hybridRecommendMapperStruct;
    }

    @GetMapping
    public Result<HybridRecommendResponseVO> hybridRecommend() {
        Long userId = UserContext.getUserId();
        return Result.success(hybridRecommendMapperStruct.toResponseVO(hybridRecommendService.recommend(userId)));
    }
}
