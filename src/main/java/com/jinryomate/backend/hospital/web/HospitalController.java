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

                    **이름과 주소를 돌려줍니다.** 심평원 응답에는 전화·좌표·의사 수까지 있지만
                    화면이 안 쓰는 것은 내지 않습니다. 지도가 들어오면 그때 좌표를 더합니다.

                    **주소가 있어야 같은 이름의 다른 지점을 구별할 수 있습니다.** `○○의원` 은
                    검색하면 여러 곳이 같은 줄로 보입니다. 드물게 `address` 가 `null` 인
                    병원도 있습니다.

                    **상류가 죽으면 503입니다. 빈 목록으로 오지 않습니다.**
                    `{"hospitals":[],"totalCount":0}` 은 **정말로 그런 병원이 없는 것**이니
                    "검색 결과 없음" 으로 안내하시면 됩니다. 503이면 "잠시 뒤 다시" 입니다.

                    **같은 질의는 캐시에서 바로 옵니다.** 상류가 느려서(중앙값 1.6초) 결과가
                    있는 질의는 6시간, 0건은 5분 붙들어 둡니다.

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
