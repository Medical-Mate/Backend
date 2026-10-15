package com.jinryomate.backend.hospital.query;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 병원 검색어에서 지역을 푼다. {@code 노원 피부과} → 노원구에서 이름에 {@code 피부과} 가 든 곳.
 *
 * <p>사람들은 병원을 <b>[지역 진료과]</b> 로 찾는데, 검색어 전체를 이름 검색에 넣으면 "이름에
 * '노원 피부과'가 들어간 병원"을 찾게 되어 0건이다. 심평원은 지역 코드 필터를 이미 받는다.
 *
 * <p><b>지역만 푼다. 진료과는 풀지 않는다.</b> 남은 글자는 지금처럼 이름 검색으로 간다 —
 * 사용자가 기대하는 것은 피부과 간판을 단 곳이다. 진료과목 필터({@code dgsbjtCd})로 바꾸면
 * 결과가 8배로 늘고 대부분 가정의학과·내과 의원이다. 증상으로 진료과를 고르는 경로도 없다.
 *
 * <p><b>못 풀면 지금 방식 그대로다.</b> 지역이 없거나, 겹치거나({@code 중구} 는 여섯 곳),
 * 지역이 둘 이상이면 검색어 전체를 이름 검색으로 보낸다. 되던 검색은 계속 되고 되는 것만 는다.
 * 겹치는 지역 하나를 아무거나 고르면 "서울 중구를 찾았는데 대구 중구가 나온다"가 된다.
 *
 * <p><b>해석과 찾기를 나눠 둔다.</b> 찾기가 지금은 심평원 필터지만 목록을 DB 에 적재하게 되어도
 * 이 해석기는 그대로 쓴다.
 */
@Slf4j
@Component
public class HospitalQueryResolver {

    private static final String RESOURCE = "hospital/sggu.tsv";

    /** {@code 서울 노원구} · {@code 경기 수원 팔달구} 처럼 지역은 세 단어까지다. */
    private static final int MAX_PHRASE = 3;

    /** 광역시 구는 도시를 {@code 부산시}·{@code 부산광역시} 로도 쓴다. */
    private static final Set<String> METRO = Set.of("부산", "인천", "대구", "광주", "대전", "울산");

    /**
     * 시도를 부르는 이름들. 코드표의 시도 이름은 {@code 서울}·{@code 전남광주} 같은 약칭뿐이라
     * 사람들이 실제로 쓰는 꼴을 여기서 붙인다.
     *
     * <p><b>{@code 광주} 는 시도로 받지 않는다.</b> 광주 구가 {@code 전남광주}(360000) 로
     * 옮겨 가 시도 필터로는 전남 군까지 섞이고, 경기 광주시와도 겹친다. 시군구 쪽에서
     * 겹침으로 처리되어 이름 검색으로 간다.
     */
    private static final Map<String, List<String>> SIDO_ALIASES = Map.ofEntries(
            Map.entry("110000", List.of("서울", "서울시", "서울특별시")),
            Map.entry("210000", List.of("부산", "부산시", "부산광역시")),
            Map.entry("220000", List.of("인천", "인천시", "인천광역시")),
            Map.entry("230000", List.of("대구", "대구시", "대구광역시")),
            Map.entry("250000", List.of("대전", "대전시", "대전광역시")),
            Map.entry("260000", List.of("울산", "울산시", "울산광역시")),
            Map.entry("310000", List.of("경기", "경기도")),
            Map.entry("320000", List.of("강원", "강원도", "강원특별자치도")),
            Map.entry("330000", List.of("충북", "충청북도")),
            Map.entry("340000", List.of("충남", "충청남도")),
            Map.entry("350000", List.of("전북", "전라북도", "전북특별자치도")),
            Map.entry("360000", List.of("전남", "전라남도", "전남광주")),
            Map.entry("370000", List.of("경북", "경상북도")),
            Map.entry("380000", List.of("경남", "경상남도")),
            Map.entry("390000", List.of("제주", "제주도", "제주특별자치도")),
            Map.entry("410000", List.of("세종", "세종시", "세종특별자치시")));

    /** 시군구 한 곳. */
    record Sggu(String sidoCd, String sgguCd) {}

    /** 시도를 부르는 말 → 시도 코드. */
    private final Map<String, String> sidoByAlias = new HashMap<>();

    /** 시군구를 부르는 말 → 그 말이 가리키는 곳들. 둘 이상이면 겹치는 이름이다. */
    private final Map<String, Set<Sggu>> sgguByAlias = new HashMap<>();

    public HospitalQueryResolver() {
        SIDO_ALIASES.forEach((code, aliases) -> aliases.forEach(a -> sidoByAlias.put(a, code)));

        int count = 0;
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                // 도시가 없는 행은 그 칸이 비어 있다. split 이 빈 칸을 버리지 않게 -1.
                String[] cols = line.split("\t", -1);
                index(new Sggu(cols[1], cols[0]), cols[1], cols[3], cols[4], cols[5]);
                count++;
            }
        } catch (Exception e) {
            // 리소스는 저장소에 같이 들어 있다. 여기서 실패하면 빌드가 잘못된 것이다.
            throw new IllegalStateException("시군구 코드표를 읽을 수 없습니다: " + RESOURCE, e);
        }
        log.info("시군구 코드표 로드 sggu={} aliases={}", count, sgguByAlias.size());
    }

    /**
     * 한 시군구를 부를 수 있는 말을 전부 등록한다. 노원구라면 {@code 노원구} · {@code 노원} ·
     * {@code 서울 노원구} · {@code 서울특별시 노원} …, 수원 팔달구라면 거기에 {@code 수원 팔달구} ·
     * {@code 수원시 팔달구} · {@code 수원팔달구} 가 더해진다.
     */
    private void index(Sggu sggu, String sidoCd, String fullName, String city, String base) {
        List<String> bases = new ArrayList<>(List.of(base));
        // 구·시·군을 뗀 꼴(노원). 한 글자만 남으면(중구 → 중) 받지 않는다. 너무 흔한 글자다.
        if (base.length() >= 3 && "구시군".indexOf(base.charAt(base.length() - 1)) >= 0) {
            bases.add(base.substring(0, base.length() - 1));
        }

        List<String> cities = new ArrayList<>();
        if (!city.isEmpty()) {
            cities.add(city);
            cities.add(city + "시");
            if (METRO.contains(city)) {
                cities.add(city + "광역시");
            }
        }
        List<String> sidos = SIDO_ALIASES.getOrDefault(sidoCd, List.of());

        put(fullName, sggu);
        for (String b : bases) {
            put(b, sggu);
            for (String c : cities) {
                put(c + " " + b, sggu);
                put(c + b, sggu);
                for (String s : sidos) {
                    put(s + " " + c + " " + b, sggu);
                }
            }
            for (String s : sidos) {
                put(s + " " + b, sggu);
            }
        }
        // 도시만 친 경우(수원). 구가 여럿이라 하나로 못 거른다 — 겹침으로 남아 이름 검색으로 간다.
        for (String c : cities) {
            put(c, sggu);
        }
    }

    private void put(String alias, Sggu sggu) {
        sgguByAlias.computeIfAbsent(alias, k -> new LinkedHashSet<>()).add(sggu);
    }

    /**
     * 검색어를 지역과 이름으로 나눈다.
     *
     * <p>앞에서부터 가장 긴 지역 이름을 먼저 맞춘다. {@code 서울 중구} 를 {@code 서울} 과
     * {@code 중구} 로 따로 읽으면 겹침이 되지만, 한 덩어리로 읽으면 한 곳이다.
     */
    public HospitalQuery resolve(String query) {
        String[] tokens = query.trim().split("\\s+");

        List<String> regions = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        int i = 0;
        while (i < tokens.length) {
            int matched = 0;
            for (int len = Math.min(MAX_PHRASE, tokens.length - i); len >= 1; len--) {
                String phrase = String.join(" ", Arrays.copyOfRange(tokens, i, i + len));
                if (sidoByAlias.containsKey(phrase) || sgguByAlias.containsKey(phrase)) {
                    regions.add(phrase);
                    matched = len;
                    break;
                }
            }
            if (matched == 0) {
                rest.add(tokens[i]);
                i++;
            } else {
                i += matched;
            }
        }

        // 지역이 없거나 둘 이상(노원 강남)이면 지금 하던 대로 검색어 전체를 이름으로.
        if (regions.size() != 1) {
            return HospitalQuery.byName(query);
        }

        String region = regions.get(0);
        String name = rest.isEmpty() ? null : String.join(" ", rest);

        // 시도가 먼저다. 제주·세종은 시도이면서 시군구 이름이기도 한데, 시도가 더 넓어 안전하다.
        String sidoCd = sidoByAlias.get(region);
        if (sidoCd != null) {
            return new HospitalQuery(name, sidoCd, null);
        }

        Set<Sggu> candidates = sgguByAlias.get(region);
        if (candidates.size() != 1) {
            // 중구 · 고성 · 광주 · 수원(구가 넷). 하나를 골라 엉뚱한 곳을 보여 주느니 거르지 않는다.
            return HospitalQuery.byName(query);
        }
        Sggu sggu = candidates.iterator().next();
        return new HospitalQuery(name, sggu.sidoCd(), sggu.sgguCd());
    }
}
