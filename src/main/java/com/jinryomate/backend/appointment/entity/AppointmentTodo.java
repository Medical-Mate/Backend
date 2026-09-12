package com.jinryomate.backend.appointment.entity;

/**
 * 진료 전 할 일 한 줄. 화면 {@code 1r-4} 에서 적고 {@code 1r-2} 에서 체크한다.
 *
 * <p>예: {@code 달라진 증상 있으면 카드 수정} · {@code 보험 서류 챙기기}
 *
 * <p>순서가 화면 순서다. 정렬 컬럼을 따로 두지 않고 배열 순서를 그대로 쓴다 — 목록째
 * 저장하므로 순서가 흐트러질 자리가 없다.
 */
public record AppointmentTodo(String text, boolean done) {}
