package com.jinryomate.backend.demo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public final class DemoEventDtos {

    private DemoEventDtos() {
    }

    /**
     * 이벤트 묶음.
     *
     * <p><b>배치가 필수입니다.</b> 이벤트 하나에 요청 하나면 IP 당 30회/분에 바로 걸립니다 —
     * 문답 한 세션이 이미 호출 13회를 쓰는데 이벤트가 25개 더 붙습니다.
     */
    @Schema(description = "웹 데모 이벤트 묶음")
    public record EventBatch(
            @Schema(description = "한 번에 최대 50개")
            @NotEmpty(message = "이벤트가 비어 있습니다.")
            @Size(max = 50, message = "한 번에 50개까지입니다.")
            @Valid
            List<Item> events
    ) {}

    /**
     * 이벤트 하나.
     *
     * <p><b>별칭을 열지 않습니다.</b> 이름이 어긋나면 검증에 걸려 400 이 납니다 — 조용히
     * 떨어지는 자리가 아니라서 별칭으로 계약을 흐릴 이유가 없습니다.
     */
    @Schema(description = "이벤트 한 줄")
    public record Item(

            @Schema(description = "DemoEventCatalog 안의 이름", example = "intake.turn_sent")
            @NotNull(message = "이벤트 이름이 필요합니다.")
            @Size(max = 48, message = "이벤트 이름은 48자 이내입니다.")
            String event,

            /*
             * 오프셋을 반드시 포함해 받는다. 시각만 오면 파싱에서 떨어져 400 이 난다 —
             * 조용히 UTC 로 해석해서 "저녁에 이탈이 많다" 를 아홉 시간 틀리게 만드는 것보다 낫다.
             */
            @Schema(description = "브라우저가 찍은 시각. 오프셋 포함", example = "2026-09-21T14:03:22+09:00")
            @NotNull(message = "이벤트 시각이 필요합니다.")
            OffsetDateTime occurredAt,

            /*
             * 문답 세션 id 가 아니다. 같은 값을 쓰면 이벤트가 카드와 이어져, 원문을
             * 한 글자도 안 담아도 누가 무엇을 말했는지가 복원된다.
             */
            @Schema(description = "브라우저 세션마다 만드는 난수. 문답 세션 id 가 아님")
            @NotNull(message = "세션 키가 필요합니다.")
            @Pattern(regexp = "[A-Za-z0-9_-]{8,32}", message = "세션 키 형식이 올바르지 않습니다.")
            String sessionId,

            @Schema(description = "같은 밀리초에 여러 개가 와도 순서가 남도록")
            @Min(value = 1, message = "순번은 1부터입니다.")
            @Max(value = 100_000, message = "순번이 너무 큽니다.")
            int seq,

            @Schema(description = "지금은 web 하나", example = "web")
            @NotNull(message = "surface 가 필요합니다.")
            @Pattern(regexp = "web|app", message = "surface 는 web 또는 app 입니다.")
            String surface,

            @Schema(description = "배포와 지표를 잇는다", example = "web-2026.09.21")
            @Size(max = 32, message = "build 는 32자 이내입니다.")
            @Pattern(regexp = "[A-Za-z0-9._-]{0,32}", message = "build 형식이 올바르지 않습니다.")
            String build,

            @Schema(description = "이벤트마다 다름. 카탈로그에 없는 키는 400")
            Map<String, Object> props
    ) {}

    /**
     * 받은 개수만 돌려준다.
     *
     * <p>브라우저는 {@code sendBeacon} 으로 던지고 응답을 안 봅니다. 이 숫자는 사람이
     * 개발자 도구에서 확인할 때 씁니다.
     */
    public record Accepted(int accepted) {}
}
