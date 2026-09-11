package com.jinryomate.backend.hospital.web;

import com.jinryomate.backend.hospital.client.HospitalSearchClient;
import com.jinryomate.backend.hospital.dto.HospitalDtos.HospitalSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "병원", description = "병원 찾기 (화면 1m-B)")
@Validated
@RestController
@RequestMapping("/api/hospitals")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalSearchClient hospitalSearchClient;

    @Operation(
            summary = "병원 검색",
            description = """
                    이름으로 병원을 찾습니다. **부분 일치**입니다 — `서울` 로 4천 건이 넘게 나옵니다.

                    원천은 **심평원 병원정보서비스**이고 서버가 목록을 들고 있지 않습니다.
                    개원·폐원이 계속 생기는 데이터라 복사해 두면 바로 낡습니다.

                    **이름과 홈페이지만 돌려줍니다.** 심평원 응답에는 주소·전화·좌표까지 있지만
                    쓰지 않을 값을 내려보내면 앱이 무엇을 믿어야 할지 흐려집니다.

                    **`url` 이 없는 병원이 많습니다.** 특히 작은 의원은 대부분 비어 있어 `null` 이
                    정상입니다 — 화면에서 링크를 숨기면 됩니다.

                    일정 등록(`POST /api/me/appointments`)의 `clinicName` 에 이 `name` 을
                    그대로 넣으면 됩니다.
                    """)
    @GetMapping
    public HospitalSearchResponse search(
            @RequestParam("q")
            @NotBlank(message = "검색어를 입력해주세요.")
            @Size(max = 60, message = "검색어는 60자 이내입니다.")
            String query,

            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "페이지는 1부터입니다.")
            int page,

            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "1개 이상 요청해주세요.")
            @Max(value = 50, message = "한 번에 50개까지입니다.")
            int size) {
        return hospitalSearchClient.search(query, page, size);
    }
}
