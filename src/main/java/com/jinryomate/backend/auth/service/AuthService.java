package com.jinryomate.backend.auth.service;

import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.client.KakaoProfile;
import com.jinryomate.backend.auth.dto.AuthDtos.SettingsResponse;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.entity.Device;
import com.jinryomate.backend.auth.entity.RefreshToken;
import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.auth.repository.DeviceRepository;
import com.jinryomate.backend.auth.repository.RefreshTokenRepository;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.appointment.repository.AppointmentRepository;
import com.jinryomate.backend.card.repository.BriefingCardRepository;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.repository.IntakeSessionRepository;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.profile.service.HealthProfileService;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final KakaoClient kakaoClient;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final DeviceRepository deviceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final HealthProfileService healthProfileService;
    private final BriefingCardRepository briefingCardRepository;
    private final IntakeSessionRepository intakeSessionRepository;
    private final VisitRecordRepository visitRecordRepository;
    private final AppointmentRepository appointmentRepository;

    /**
     * 카카오 로그인. 처음 온 회원번호면 가입시키고, 있으면 그 사용자로 로그인한다.
     *
     * <p>동의항목으로 받은 이름·성별·출생연도가 있으면 프로필에 미리 채워 온보딩 입력을 줄인다.
     * 값이 없어도(동의 거부, 검수 전) 정상 경로다 — 온보딩에서 직접 받으면 된다.
     */
    @Transactional
    public TokenResponse loginWithKakao(String kakaoAccessToken) {
        Long kakaoId = kakaoClient.resolveKakaoId(kakaoAccessToken);

        Optional<User> existing = userRepository.findByKakaoId(kakaoId);
        boolean isNew = existing.isEmpty();
        User user = existing.orElseGet(() -> userRepository.save(User.ofKakao(kakaoId)));

        applyKakaoProfile(user, kakaoAccessToken);

        // 카카오 회원번호는 개인정보라 로그에 남기지 않는다. 내부 id만 남긴다.
        log.info("카카오 로그인 성공 userId={} isNew={}", user.getId(), isNew);

        boolean onboardingRequired = !healthProfileService.isOnboardingCompleted(user.getId());
        return issueTokens(user, onboardingRequired);
    }

    private void applyKakaoProfile(User user, String kakaoAccessToken) {
        KakaoProfile profile = kakaoClient.fetchProfile(kakaoAccessToken);
        if (profile == null) {
            return;
        }
        healthProfileService.applyKakaoValues(
                user,
                profile.name(),
                Sex.fromKakao(profile.gender()),
                profile.birthYear(),
                profile.birthMonthDay());
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        RefreshToken saved = refreshTokenRepository
                .findByTokenHash(jwtProvider.hash(rawRefreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));

        if (!saved.isUsable(Instant.now())) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요.");
        }

        // 회전: 쓴 토큰은 즉시 막고 새로 발급한다. 탈취된 토큰의 수명을 줄인다.
        saved.revoke();

        // 로그인과 같은 값을 계산한다. false 로 박아두면 앱 재실행 시 자동 로그인에서
        // 온보딩을 안 끝낸 사용자가 빈 프로필로 홈에 들어간다.
        User user = saved.getUser();
        boolean onboardingRequired = !healthProfileService.isOnboardingCompleted(user.getId());
        return issueTokens(user, onboardingRequired);
    }

    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.revokeAllByUser(findUser(userId), Instant.now());
    }

    /**
     * 탈퇴. 이 사용자의 데이터를 지우고 카카오 연결을 끊는다.
     *
     * <p>카드·문답·기록은 각 도메인이 생기는 대로 여기에 이어 붙인다.
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = findUser(userId);
        Long kakaoId = user.getKakaoId();

        deleteAllData(user);

        // 로컬 삭제 후에 호출한다. 카카오 쪽이 실패해도 탈퇴는 완료돼야 한다.
        kakaoClient.unlink(kakaoId);
        log.info("탈퇴 완료 userId={}", userId);
    }

    /**
     * 카카오에서 연결을 끊었을 때. 연결 해제 웹훅이 부른다.
     *
     * <p>이미 카카오 쪽에서 끊긴 상태이므로 {@code unlink} 를 부르지 않는다.
     *
     * <p><b>없는 회원번호여도 조용히 넘어간다.</b> 웹훅은 재전송될 수 있고, 우리가 먼저
     * 탈퇴 처리한 뒤에 도착할 수도 있다. 그때 오류를 내면 카카오가 실패로 보고 재시도한다.
     */
    @Transactional
    public void withdrawByKakaoId(Long kakaoId) {
        userRepository.findByKakaoId(kakaoId).ifPresentOrElse(
                user -> {
                    Long userId = user.getId();
                    deleteAllData(user);
                    log.info("카카오 연결 해제로 삭제 userId={}", userId);
                },
                () -> log.info("카카오 연결 해제 — 이미 없는 회원이라 넘어간다"));
    }

    /**
     * 이 사용자의 데이터를 전부 지운다.
     *
     * <p>일정 → 기록 → 카드 → 세션 → 프로필 순이다. 뒤쪽이 앞쪽을 참조하고 있어
     * 순서를 바꾸면 외래키에 걸린다. 일정은 카드를 참조한다.
     *
     * <p>도메인이 늘면 여기에 줄을 더한다. {@code WithdrawCascadeTest} 가 먼저 깨진다.
     */
    private void deleteAllData(User user) {
        appointmentRepository.deleteAllByUser(user);
        visitRecordRepository.deleteAllByUser(user);
        briefingCardRepository.deleteAllByUser(user);
        intakeSessionRepository.deleteAllByUser(user);
        healthProfileService.deleteByUser(user);
        refreshTokenRepository.deleteAllByUser(user);
        deviceRepository.deleteAllByUser(user);
        userRepository.delete(user);
    }

    @Transactional
    public void registerDevice(Long userId, String pushToken, Device.Platform platform) {
        User user = findUser(userId);
        deviceRepository.findByPushToken(pushToken)
                .ifPresentOrElse(
                        device -> device.refresh(user, platform),
                        () -> deviceRepository.save(Device.of(user, pushToken, platform)));
    }

    @Transactional(readOnly = true)
    public SettingsResponse getSettings(Long userId) {
        return new SettingsResponse(findUser(userId).isVisitReminderEnabled());
    }

    @Transactional
    public SettingsResponse updateSettings(Long userId, boolean visitReminderEnabled) {
        User user = findUser(userId);
        user.changeVisitReminder(visitReminderEnabled);
        log.info("설정 변경 userId={} 진료전알림={}", userId, visitReminderEnabled);
        return new SettingsResponse(user.isVisitReminderEnabled());
    }

    private TokenResponse issueTokens(User user, boolean onboardingRequired) {
        String refreshToken = jwtProvider.generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.issue(
                user, jwtProvider.hash(refreshToken), jwtProvider.refreshExpiresAt()));

        return new TokenResponse(
                jwtProvider.issueAccessToken(user.getId()),
                refreshToken,
                jwtProvider.accessTtlSeconds(),
                onboardingRequired);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));
    }
}
