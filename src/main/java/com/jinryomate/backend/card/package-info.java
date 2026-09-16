/**
 * 브리핑 카드. 화면 S3.
 * 
 * <p>대화를 카드로 만들고, 환자가 고치고, 확정한다.
 * 확정 뒤의 변경은 새 버전으로만 남긴다 — 의사가 본 카드가 뒤바뀌면 안 된다.
 * 
 * <p>AI 응답은 저장 전에 서버가 검증한다. 축 값 80자, {@code status} 5값·{@code source}
 * 3값, 진료과는 정해진 13개 목록. 검증에 걸린 필드는 {@code unknown} 으로 저장해 환자가
 * 직접 채우게 한다 — 카드 생성이 통째로 실패해서 문답이 날아가는 일은 없어야 한다.
 *
 * <p><b>재요청하지 않는다.</b> 카드는 축 값이 쌓인 {@code state} 를 직렬화한 것이라 같은
 * {@code state} 면 같은 카드가 나온다. 검증에 걸렸다면 AI 출력이 규격을 벗어난 것이고
 * 재요청이 아니라 제보 대상이다.
 *
 * <p><b>제목은 검증하지 않는다.</b> AI 가 부위 + 기간을 결정론으로 조합해 만들어서
 * 병명이 들어갈 경로가 없다. <b>진료과도 enum 이 아니다</b> — 하나로 좁히는 순간 그게
 * 감별진단이라, {@code department_guidance} 를 배열 그대로 쓴다.
 *
 * <p><b>질문은 AI 가 만든 것이 아니다.</b> 카드의 {@code questions} 는 환자가 화면
 * {@code 1i} 에서 적은 것이고 상한은 {@code IntakeDtos.MAX_QUESTIONS} 하나가 정한다.
 * AI 후보는 {@code questionCandidates} 로 따로 온다.
 * 
 * <p>응답의 meta.pipelineVersion을 카드와 함께 저장한다.
 * 어떤 파이프라인으로 만들어진 카드인지 나중에 추적하기 위함이다.
 */
package com.jinryomate.backend.card;
