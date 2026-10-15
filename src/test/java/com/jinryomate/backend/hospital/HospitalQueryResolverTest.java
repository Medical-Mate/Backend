package com.jinryomate.backend.hospital;

import static org.assertj.core.api.Assertions.assertThat;

import com.jinryomate.backend.hospital.query.HospitalQuery;
import com.jinryomate.backend.hospital.query.HospitalQueryResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 병원 검색어에서 지역을 푸는 부분. 코드는 {@code hospital/sggu.tsv} 의 실제 값이다.
 *
 * <p>가장 중요한 것은 <b>못 풀 때 지금과 똑같이 동작하는 것</b>이다. 되던 검색이 지역 해석
 * 때문에 안 되면 개선이 아니라 회귀다.
 */
class HospitalQueryResolverTest {

    private static final String SEOUL = "110000";
    private static final String NOWON = "110022";

    private final HospitalQueryResolver resolver = new HospitalQueryResolver();

    @ParameterizedTest
    @ValueSource(strings = {"노원 피부과", "노원구 피부과", "서울 노원 피부과", "서울특별시 노원구 피부과"})
    @DisplayName("지역을 앞에 붙이면 시군구 코드와 남은 이름으로 나뉜다")
    void 지역_진료과(String query) {
        assertThat(resolver.resolve(query)).isEqualTo(new HospitalQuery("피부과", SEOUL, NOWON));
    }

    @Test
    @DisplayName("시도를 붙이면 겹치는 구 이름도 한 곳으로 좁혀진다")
    void 시도로_좁히기() {
        assertThat(resolver.resolve("서울 중구 내과"))
                .isEqualTo(new HospitalQuery("내과", SEOUL, "110017"));
        assertThat(resolver.resolve("부산 중구"))
                .isEqualTo(new HospitalQuery(null, "210000", "210008"));
        assertThat(resolver.resolve("강원 고성 의원"))
                .isEqualTo(new HospitalQuery("의원", "320000", "320001"));
    }

    @Test
    @DisplayName("심평원이 도시에 붙여 쓰는 구도 여러 꼴로 받는다")
    void 일반구() {
        HospitalQuery paldal = new HospitalQuery("내과", "310000", "310603");
        assertThat(resolver.resolve("수원 팔달구 내과")).isEqualTo(paldal);
        assertThat(resolver.resolve("수원시 팔달구 내과")).isEqualTo(paldal);
        assertThat(resolver.resolve("수원팔달구 내과")).isEqualTo(paldal);
        assertThat(resolver.resolve("팔달구 내과")).isEqualTo(paldal);
        assertThat(resolver.resolve("경기 수원 팔달구 내과")).isEqualTo(paldal);
    }

    @Test
    @DisplayName("부산진구는 부산 + 진구가 아니다")
    void 부산진구() {
        assertThat(resolver.resolve("부산진구 정형외과"))
                .isEqualTo(new HospitalQuery("정형외과", "210000", "210004"));
    }

    @Test
    @DisplayName("광주 구는 바뀐 시도 코드(전남광주)로 간다")
    void 광주_구() {
        // 옛 코드(240002)에는 병원이 한 곳뿐이다. 그쪽으로 가면 조용히 거의 0건이 된다.
        assertThat(resolver.resolve("광주 북구 내과"))
                .isEqualTo(new HospitalQuery("내과", "360000", "360802"));
    }

    @Test
    @DisplayName("지역만 치면 이름 조건 없이 지역으로만 거른다")
    void 지역만() {
        assertThat(resolver.resolve("노원")).isEqualTo(new HospitalQuery(null, SEOUL, NOWON));
        assertThat(resolver.resolve("서울 피부과")).isEqualTo(new HospitalQuery("피부과", SEOUL, null));
    }

    @Test
    @DisplayName("앞뒤·사이 공백은 지역 해석에 영향이 없다")
    void 공백() {
        assertThat(resolver.resolve("  노원   피부과 ")).isEqualTo(new HospitalQuery("피부과", SEOUL, NOWON));
    }

    @ParameterizedTest
    @ValueSource(strings = {"강북삼성병원", "서울아산", "서울대학교병원", "행복한의원"})
    @DisplayName("지역 단어가 없으면 지금처럼 검색어 전체로 이름 검색")
    void 이름_검색_그대로(String query) {
        assertThat(resolver.resolve(query)).isEqualTo(HospitalQuery.byName(query));
    }

    @Test
    @DisplayName("증상은 진료과로 풀지 않는다")
    void 증상() {
        // 진료과 사전을 두지 않는다. 증상에서 과를 고르는 것은 감별이다.
        assertThat(resolver.resolve("무릎 아픈데")).isEqualTo(HospitalQuery.byName("무릎 아픈데"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "중구 내과",       // 여섯 곳
            "고성 의원",       // 강원·경남
            "광주 내과",       // 광주광역시 구 다섯 + 경기 광주시
            "광주시 내과",     // 같은 이유
            "수원 내과",       // 구가 넷이라 한 코드로 못 거른다
            "노원 강남 피부과" // 지역이 둘
    })
    @DisplayName("지역이 겹치거나 둘 이상이면 거르지 않고 검색어 전체로 이름 검색")
    void 못_푸는_지역(String query) {
        // 아무거나 하나 고르면 "서울 중구를 찾았는데 대구 중구가 나온다" 가 된다.
        assertThat(resolver.resolve(query)).isEqualTo(HospitalQuery.byName(query));
    }

    @Test
    @DisplayName("경기를 붙이면 경기 광주시로 좁혀진다")
    void 경기_광주() {
        assertThat(resolver.resolve("경기 광주 내과"))
                .isEqualTo(new HospitalQuery("내과", "310000", "312600"));
    }

    @Test
    @DisplayName("한 글자짜리는 지역으로 읽지 않는다")
    void 한_글자() {
        // 중구 → "중" 을 받으면 "중 내과" 같은 오타가 지역 필터로 빨려 들어간다.
        assertThat(resolver.resolve("중 내과")).isEqualTo(HospitalQuery.byName("중 내과"));
    }
}
