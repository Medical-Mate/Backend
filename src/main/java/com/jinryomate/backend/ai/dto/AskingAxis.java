package com.jinryomate.backend.ai.dto;

/**
 * AI 가 다음 문장({@code reply})으로 묻는 칸. AI 계약의 {@code asking_axis}.
 *
 * <p><b>폰 추출 프롬프트의 첫 줄 재료다.</b> {@code 질문한 항목: {label} (axis={key}).}
 * 로 그대로 들어간다(AI#156). {@code label} 은 폰 프롬프트와 같은 원천에서 오므로
 * <b>가공하지 않는다</b> — 앱이 따로 표를 들면 둘이 갈라진다.
 *
 * <p>{@code state} 안에도 같은 값이 있지만 <b>거기서 꺼내지 않는다.</b> AI 가 밖으로
 * 낸 이 필드만 쓴다.
 *
 * @param key   축 키. {@code onset} · {@code character} …
 * @param label 프롬프트에 넣을 말. {@code 언제부터·어떻게 시작}
 */
public record AskingAxis(String key, String label) {}
