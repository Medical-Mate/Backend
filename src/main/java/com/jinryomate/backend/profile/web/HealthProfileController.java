package com.jinryomate.backend.profile.web;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileResponse;
import com.jinryomate.backend.profile.service.HealthProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프로필", description = "온보딩에서 받는 환자 정보 (화면 S1)")
@RestController
@RequestMapping("/api/me/health-profile")
@RequiredArgsConstructor
public class HealthProfileController {

    private final HealthProfileService healthProfileService;
    private final UserRepository userRepository;

    @Operation(
            summary = "프로필 조회",
            description = """
                    온보딩 화면 진입 시 호출하면 **카카오에서 받은 값이 채워져** 옵니다.
                    입력칸의 기본값으로 쓰세요.

                    - `sources` — 각 값이 카카오에서 왔는지(`KAKAO`) 사용자가 넣은 것인지(`SELF_INPUT`).
                      "카카오에서 가져왔어요" 같은 안내를 띄울 때 씁니다
                    - `onboardingCompleted` — 온보딩을 실제로 마쳤는지.
                      **카카오 값이 채워진 것만으로는 `false`입니다**
                    - `canStartIntake` — 문답을 시작할 수 있는지. 나이·성별이 있어야 `true`입니다.
                      이 둘은 의사용 카드 헤더에 반드시 찍히기 때문입니다

                    프로필이 아직 없으면 빈 값들이 내려옵니다. 404가 아닙니다.
                    """)
    @GetMapping
    public HealthProfileResponse get(@AuthenticationPrincipal Long userId) {
        return healthProfileService.get(userId);
    }

    @Operation(
            summary = "온보딩 저장",
            description = """
                    온보딩 5단계를 모아 **한 번에** 저장합니다. 단계별 저장은 없습니다.

                    - `birthYear` — 나이가 아니라 **출생연도**입니다. 나이는 해가 바뀌면 낡습니다
                    - `birthMonthDay` — `MM-dd` 형식, 선택값. 있으면 만 나이를 정확히 계산합니다.
                      없으면 `올해 - 출생연도`라 생일 전인 사람은 한 살 많게 나옵니다.
                      카카오에서 받은 생일이 `MMDD`로 왔다면 그대로 넘겨주세요(변환은 서버가 합니다)
                    - `medications` / `conditions` / `allergies` — 각각 `status`를 함께 보냅니다

                    `status` 세 값을 반드시 구분해주세요.

                    | 값 | 뜻 |
                    | --- | --- |
                    | `KNOWN` | 값이 있음 |
                    | `NONE` | "없어요" |
                    | `UNKNOWN` | "잘 모르겠어요" |

                    `NONE`과 `UNKNOWN`을 섞으면 의사용 카드에 "알레르기: 본인 확인 못 함"을 찍을 수 없습니다.

                    여기서 저장한 값은 출처가 `SELF_INPUT`이 되어, **다음 로그인 때 카카오 값으로 덮이지 않습니다.**
                    """)
    @PutMapping
    public HealthProfileResponse put(@AuthenticationPrincipal Long userId,
                                     @Valid @RequestBody HealthProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));
        return healthProfileService.completeOnboarding(user, request);
    }
}
