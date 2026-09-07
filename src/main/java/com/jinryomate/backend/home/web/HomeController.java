package com.jinryomate.backend.home.web;

import com.jinryomate.backend.home.dto.HomeDtos.HomeResponse;
import com.jinryomate.backend.home.service.HomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "홈", description = "앱을 열면 처음 보는 화면 (화면 1n)")
@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class HomeController {

    private final HomeService homeService;

    @Operation(
            summary = "홈 요약",
            description = """
                    홈에 필요한 네 덩어리를 **한 번에** 줍니다.

                    | 필드 | 화면 |
                    | --- | --- |
                    | `lastVisitedOn` | "지난 진료 후 12일이 지났어요" |
                    | `nextAppointment` | "다가오는 일정" · D-day |
                    | `inProgressSession` | "이어서 하기" · "3단계 중 2단계까지" |
                    | `recentCards` | "최근 브리핑 카드" 3건 |

                    **문구와 D-day 는 서버가 만들지 않습니다.** 숫자와 날짜만 옵니다.
                    서버가 문자열로 내려주면 말투를 바꿀 때마다 배포해야 하고,
                    시간대가 어긋나면 날짜 수가 하루 틀립니다.

                    **신규 사용자는 전부 `null` 또는 빈 배열입니다.** 404 가 아닙니다 —
                    이걸 보고 "증상 정리 유도" 화면(1n-2)을 그리세요.
                    """)
    @GetMapping("/home")
    public HomeResponse home(@AuthenticationPrincipal Long userId) {
        return homeService.get(userId);
    }
}
