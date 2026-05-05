package com.sy.course_system.controller.client;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sy.course_system.common.Result;
import com.sy.course_system.common.UserContext;
import com.sy.course_system.dto.onboarding.OnboardingSubmitDTO;
import com.sy.course_system.service.OnboardingService;
import com.sy.course_system.vo.OnboardingOptionsVO;
import com.sy.course_system.vo.OnboardingStatusVO;

@RestController
@RequestMapping("/onboarding")
public class OnboardingController {

    @Autowired
    private OnboardingService onboardingService;

    @GetMapping("/options")
    public Result<OnboardingOptionsVO> options() {
        return Result.success(onboardingService.getOptions());
    }

    @PostMapping("/submit")
    public Result<String> submit(@RequestBody OnboardingSubmitDTO submitDTO) {
        try {
            onboardingService.submit(UserContext.getUserId(), submitDTO);
            return Result.success("提交成功");
        } catch (IllegalArgumentException ex) {
            return Result.error(400, ex.getMessage());
        } catch (RuntimeException ex) {
            return Result.error(500, "提交失败");
        }
    }

    @GetMapping("/status")
    public Result<OnboardingStatusVO> status() {
        return Result.success(onboardingService.getStatus(UserContext.getUserId()));
    }
}
