package com.jinryomate.backend.card.entity;

/**
 * 카드에 찍히는 추천 진료과.
 *
 * <p>AI가 준 값이 이 목록에 없으면 저장하지 않는다. 자유 텍스트를 허용하면
 * 의사가 보는 카드에 존재하지 않는 진료과가 찍힐 수 있다.
 */
public enum Department {

    INTERNAL_MEDICINE("내과"),
    ORTHOPEDICS("정형외과"),
    NEUROLOGY("신경과"),
    NEUROSURGERY("신경외과"),
    DERMATOLOGY("피부과"),
    OTOLARYNGOLOGY("이비인후과"),
    OPHTHALMOLOGY("안과"),
    OBSTETRICS_GYNECOLOGY("산부인과"),
    UROLOGY("비뇨의학과"),
    PSYCHIATRY("정신건강의학과"),
    FAMILY_MEDICINE("가정의학과"),
    PEDIATRICS("소아청소년과"),
    REHABILITATION("재활의학과"),
    GENERAL_SURGERY("외과"),
    DENTISTRY("치과");

    private final String label;

    Department(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
