package com.sy.course_system.service;

import com.sy.course_system.dto.onboarding.OnboardingSubmitDTO;
import com.sy.course_system.vo.OnboardingOptionsVO;
import com.sy.course_system.vo.OnboardingStatusVO;

public interface OnboardingService {

    OnboardingOptionsVO getOptions();

    void submit(Long userId, OnboardingSubmitDTO submitDTO);

    OnboardingStatusVO getStatus(Long userId);
}
