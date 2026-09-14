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
                    홈에 필요한 것을 **한 번에** 줍니다.

                    | 필드 | 화면 |
                    | --- | --- |
                    | `lastVisitedOn` | "지난 진료 후 12일이 지났어요" |
                    | `nextAppointment` | "다가오는 일정" · D-day · **오늘 진료** |
                    | `pendingRecordOn` | "9월 12일 진료, 기록이 아직 없어요" |
                    | `inProgressSession` | "이어서 하기" · "3단계 중 2단계까지" |
                    | `recentCards` | "최근 브리핑 카드" 3건 |

                    **문구와 D-day 는 서버가 만들지 않습니다.** 숫자와 날짜만 옵니다.
                    서버가 문자열로 내려주면 말투를 바꿀 때마다 배포해야 하고,
                    시간대가 어긋나면 날짜 수가 하루 틀립니다.

                    ### `nextAppointment` 는 오늘 일정을 **하루 종일** 담습니다

                    **시각이 지나도 안 빠집니다.** 날짜로만 견주기 때문입니다 — 시각까지
                    견주면 오전 10시 진료가 10시 1분에 사라져서, "오늘 진료는 어떠셨어요?"
                    를 그릴 수가 없습니다.

                    그러니 오늘 일정을 따로 드릴 필요가 없습니다. `scheduledOn` 이 오늘이면
                    그게 오늘 진료이고, `scheduledTime` 과 현재 시각을 견주어 **"오늘 진료가
                    있어요" / "오늘 진료는 어떠셨어요?"** 를 가르시면 됩니다.

                    `scheduledTime` 이 `null`(시간 미정)이면 견줄 것이 없습니다. 그때는
                    하루 종일 "오늘 진료가 있어요" 로 두시는 게 맞을 것 같습니다.

                    ### `pendingRecordOn` — 기록이 빠진 지난 일정

                    진료 후 기록이 아직 없는 지난 일정 가운데 **가장 최근 날**입니다.
                    없으면 `null` 입니다.

                    **14일까지만 거슬러 봅니다.** 그보다 오래된 진료를 이제 와 재촉하면
                    알림이 아니라 잔소리이고, 환자도 그때 무슨 말을 들었는지 이미 흐릿합니다.

                    **기록이 있는지는 날짜로 견줍니다.** 일정과 기록을 잇는 열쇠가 없어서요 —
                    기록은 카드에 붙는데 일정은 카드 없이도 만들 수 있습니다. 그래서 "그날
                    날짜로 남긴 기록이 있는가" 로 판단합니다.

                    > 한 가지 한계가 있습니다. 진료를 다녀와 **다음 날** 기록하면서 날짜를
                    > 그날로 두면, 그 일정은 계속 "기록 없음" 으로 남습니다. 저희는 안 적은
                    > 것과 구별하지 못합니다. 기록 화면에서 **진료일 기본값을 일정 날짜로**
                    > 채워 주시면 거의 없어집니다.

                    **취소한 일정은 세지 않습니다.** `DONE` 은 거르지 않는데, 서버가 그 값을
                    세우는 곳이 없어서 믿을 수 없기 때문입니다 — 앱이 `DONE` 을 "다녀왔지만
                    기록은 안 함" 으로 쓰고 계시면 알려주세요.

                    **신규 사용자는 전부 `null` 또는 빈 배열입니다.** 404 가 아닙니다 —
                    이걸 보고 "증상 정리 유도" 화면(1n-2)을 그리세요.
                    """)
    @GetMapping("/home")
    public HomeResponse home(@AuthenticationPrincipal Long userId) {
        return homeService.get(userId);
    }
}
