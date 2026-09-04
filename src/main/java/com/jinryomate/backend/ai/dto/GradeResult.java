package com.jinryomate.backend.ai.dto;

/**
 * 되묻기 채점 결과.
 *
 * @param correct    맞았는지. <b>틀려도 넘어간다.</b> 시험이 아니라 이해 확인이다
 * @param correction 환자에게 보여줄 정정 문구. 맞았으면 격려 한 줄
 */
public record GradeResult(boolean correct, String correction) {}
