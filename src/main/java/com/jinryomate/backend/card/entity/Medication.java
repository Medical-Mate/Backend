package com.jinryomate.backend.card.entity;

/**
 * 카드에 찍히는 복용약 한 줄.
 *
 * @param name 약 이름. 예: {@code 이부프로펜}
 * @param note 복용 조건. 예: {@code 증상 시}. 없으면 {@code null}
 */
public record Medication(String name, String note) {}
