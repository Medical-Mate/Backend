package com.jinryomate.backend.profile.web;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileResponse;
import com.jinryomate.backend.profile.service.HealthProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/health-profile")
@RequiredArgsConstructor
public class HealthProfileController {

    private final HealthProfileService healthProfileService;
    private final UserRepository userRepository;

    /** 온보딩 화면이 기본값으로 띄울 값. 카카오에서 받은 게 있으면 채워져 있다. */
    @GetMapping
    public HealthProfileResponse get(@AuthenticationPrincipal Long userId) {
        return healthProfileService.get(userId);
    }

    /** 온보딩 완료. 5단계를 모아 한 번에 저장한다. */
    @PutMapping
    public HealthProfileResponse put(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody HealthProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));
        return healthProfileService.completeOnboarding(user, request);
    }
}
