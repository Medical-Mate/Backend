package com.jinryomate.backend.handoff.web;

import com.jinryomate.backend.handoff.dto.HandoffDtos.HandoffView;
import com.jinryomate.backend.handoff.service.HandoffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 없이 열리는 공유 뷰.
 *
 * <p>{@code /api} 밑에 두지 않는다. {@code SecurityConfig} 에서 {@code /s/**} 만
 * 열어두므로, 경로가 분리돼 있어야 실수로 다른 엔드포인트까지 열리지 않는다.
 */
@Tag(name = "공유 뷰", description = "인증 없이 열리는 읽기 전용 카드")
@RestController
@RequiredArgsConstructor
public class SharedCardController {

    private final HandoffService handoffService;

    @Operation(
            summary = "공유된 카드 열람",
            description = """
                    토큰만으로 열립니다. 로그인이 필요 없습니다.

                    폐기·만료·없는 토큰은 **모두 404** 입니다. "만료되었습니다"라고 구분해 알려주면
                    그 토큰이 존재했다는 사실이 새어 나갑니다.

                    응답에 `cardId` 같은 내부 식별자는 담기지 않습니다.
                    """)
    @GetMapping("/s/{token}")
    public HandoffView open(@PathVariable String token) {
        return handoffService.openShared(token);
    }
}
