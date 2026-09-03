package com.jinryomate.backend.profile.service;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileResponse;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HealthProfileService {

    private final HealthProfileRepository repository;

    @Transactional(readOnly = true)
    public HealthProfileResponse get(Long userId) {
        return repository.findByUserId(userId)
                .map(HealthProfileResponse::from)
                .orElseGet(HealthProfileResponse::empty);
    }

    /** 온보딩 저장. 프로필이 없으면 만든다. */
    @Transactional
    public HealthProfileResponse completeOnboarding(User user, HealthProfileRequest request) {
        HealthProfile profile = repository.findByUserId(user.getId())
                .orElseGet(() -> repository.save(HealthProfile.emptyFor(user)));

        profile.completeOnboarding(
                request.name(),
                request.birthYear(),
                request.sex(),
                request.medications().status(), request.medications().items(),
                request.conditions().status(), request.conditions().items(),
                request.allergies().status(), request.allergies().text());

        // 증상·복용약은 민감정보라 값을 로그에 남기지 않는다.
        log.info("온보딩 완료 userId={}", user.getId());
        return HealthProfileResponse.from(profile);
    }

    /**
     * 로그인할 때 카카오에서 받은 값을 프로필에 채운다.
     *
     * <p>사용자가 온보딩에서 고친 필드는 덮어쓰지 않는다. 이 구분이 없으면
     * 이름을 고쳐도 재로그인마다 카카오 값으로 되돌아간다.
     *
     * <p>카카오 동의를 거부해 값이 하나도 없으면 아무것도 하지 않는다.
     */
    @Transactional
    public void applyKakaoValues(User user, String name, Sex sex, Integer birthYear) {
        if (name == null && sex == null && birthYear == null) {
            return;
        }
        HealthProfile profile = repository.findByUserId(user.getId())
                .orElseGet(() -> repository.save(HealthProfile.emptyFor(user)));
        profile.applyKakaoValues(name, sex, birthYear);
    }

    /** 온보딩을 마쳤는지. 로그인 응답에서 앱이 어느 화면으로 갈지 정하는 데 쓴다. */
    @Transactional(readOnly = true)
    public boolean isOnboardingCompleted(Long userId) {
        return repository.findByUserId(userId)
                .map(HealthProfile::isOnboardingCompleted)
                .orElse(false);
    }

    /** 문답을 시작할 수 있는지. 나이·성별이 있어야 한다. */
    @Transactional(readOnly = true)
    public boolean canStartIntake(Long userId) {
        return repository.findByUserId(userId)
                .map(HealthProfile::canStartIntake)
                .orElse(false);
    }

    @Transactional
    public void deleteByUser(User user) {
        repository.deleteByUser(user);
    }
}
