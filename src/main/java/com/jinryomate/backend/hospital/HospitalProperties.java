package com.jinryomate.backend.hospital;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 병원 검색(심평원 병원정보서비스) 설정.
 *
 * <p><b>{@code serviceKey} 는 발급받은 문자열을 그대로 넣는다.</b> 디코딩하거나 다듬지 않는다 —
 * 아래 이유 때문이다.
 *
 * <p>공공데이터포털이 주는 키는 이미 퍼센트 인코딩돼 있다({@code …tA%3D%3D}). 이걸 디코딩해서
 * 넣고 HTTP 클라이언트가 다시 인코딩하게 두면 <b>인증은 통과한 것처럼 보이는데 결과가 0건으로
 * 온다.</b> {@code resultMsg} 도 {@code NORMAL SERVICE} 라 실패한 줄 모른다. 실제로 확인했다.
 *
 * <pre>
 * 인코딩 키 그대로   →  "서울" 4339건
 * 디코딩 후 재인코딩 →  0건 (오류 메시지 없음)
 * </pre>
 *
 * <p>그래서 {@link com.jinryomate.backend.hospital.client.HttpHospitalSearchClient} 가 URI 를
 * 직접 조립한다.
 */
@ConfigurationProperties(prefix = "hospital")
public record HospitalProperties(
        String baseUrl,
        String serviceKey,
        Duration timeout
) {}
