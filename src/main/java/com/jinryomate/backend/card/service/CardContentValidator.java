package com.jinryomate.backend.card.service;

import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.intake.dto.IntakeDtos;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * AI 가 준 카드를 저장 전에 검증한다.
 *
 * <p><b>카드 생성이 통째로 실패해서 문답이 날아가는 일은 없어야 한다.</b> 그래서 걸린 값을
 * 예외로 던지지 않고 {@link AxisStatus#UNKNOWN} 으로 낮춰 저장하고, 어느 것이 걸렸는지
 * 목록으로 돌려준다. 앱은 그 목록을 보고 "이 항목은 직접 채워주세요"를 띄운다.
 *
 * <p><b>재요청은 하지 않는다.</b> 카드는 축 값이 쌓인 {@code state} 를 직렬화한 것이라
 * 같은 {@code state} 면 같은 카드가 바이트까지 같게 나온다 — 다시 불러도 결과가 같다.
 * 검증에 걸렸다면 그건 AI 쪽 출력이 규격을 벗어난 것이고, 재요청이 아니라 버그 제보 대상이다.
 */
@Component
public class CardContentValidator {

    /** 낭독 모드 큰 글자 기준. 이보다 길면 화면에서 잘린다. */
    private static final int MAX_AXIS_VALUE = 80;

    /**
     * 질문 상한은 <b>문답 쪽이 정한다</b>({@link IntakeDtos#MAX_QUESTIONS}).
     *
     * <p><b>여기 값을 따로 들고 있다가 겪었다.</b> 저장은 통과하는데 카드를 만들 때 앞에서
     * 잘려서, 앱에서는 저장이 성공했는데 카드에는 세 개만 있었다(#132). 두 곳이 같은 것을
     * 세는 한 값도 한 곳에서 와야 한다.
     *
     * <p><b>이 검사는 사실 AI 출력에 안 걸린다.</b> {@code CardContent.questions} 의 출처는
     * {@code CardAssembler} 의 {@code session.getQuestions()} 한 줄뿐이라 <b>환자가 직접 쓴
     * 글</b>이다. AI 가 만든 후보는 {@code question_candidates} 로 따로 오고 여기 섞이지
     * 않는다. 그래서 길이도 AI 기준(40자)이 아니라 환자가 쓸 수 있는 만큼이다.
     */
    private static final int MAX_QUESTIONS = IntakeDtos.MAX_QUESTIONS;
    private static final int MAX_QUESTION_LENGTH = IntakeDtos.MAX_QUESTION_LENGTH;

    /**
     * 온톨로지에 등장하는 진료과 전부.
     *
     * <p><b>enum 을 버렸다고 검증까지 버린 것은 아니다.</b> 하나로 좁히지 않을 뿐,
     * 값이 이 목록 밖이면 거부한다. 늘어나면 AI 담당이 먼저 알려준다.
     */
    private static final Set<String> DEPARTMENTS = Set.of(
            "가정의학과", "내과", "비뇨의학과", "산부인과", "소화기내과", "신경과", "신경외과",
            "심장내과", "안과", "이비인후과", "정형외과", "피부과", "호흡기내과");

    public record Result(CardContent content, List<String> rejectedFields) {}

    public Result validate(CardContent content) {
        List<String> rejected = new ArrayList<>();

        Map<String, CardAxis> axes = new LinkedHashMap<>();
        content.axes().forEach((name, axis) -> axes.put(name, checkAxis(name, axis, rejected)));

        List<String> questions = new ArrayList<>();
        for (String q : content.questions()) {
            if (q == null || q.isBlank()) {
                continue;
            }
            if (q.length() > MAX_QUESTION_LENGTH) {
                rejected.add("questions");
                continue;
            }
            questions.add(q);
        }
        if (questions.size() > MAX_QUESTIONS) {
            // 넘치면 버리지 않고 앞에서 자른다. AI 가 순서로 중요도를 표현하기 때문이다.
            rejected.add("questions");
            questions = new ArrayList<>(questions.subList(0, MAX_QUESTIONS));
        }

        List<String> departments = new ArrayList<>();
        for (String d : content.departmentGuidance()) {
            if (DEPARTMENTS.contains(d)) {
                departments.add(d);
            } else {
                rejected.add("departmentGuidance");
            }
        }

        CardContent checked = new CardContent(
                content.title(),
                content.chiefComplaint(),
                axes,
                List.copyOf(content.redFlags()),
                List.copyOf(content.patientNotes()),
                List.copyOf(questions),
                List.copyOf(departments),
                content.departmentGuidanceSource(),
                content.completeness(),
                content.minimallyComplete());

        return new Result(checked, List.copyOf(rejected));
    }

    /**
     * 축 하나를 검증한다.
     *
     * <p>{@code title} 은 검증하지 않는다. AI 가 부위 + 기간을 결정론으로 조합해 만들어
     * <b>병명이 들어갈 경로가 없다</b>. 검사를 느슨하게 한 것이 아니라 검사할 대상이 없다.
     */
    private CardAxis checkAxis(String name, CardAxis axis, List<String> rejected) {
        String value = axis.getValue();
        if (value != null && value.length() > MAX_AXIS_VALUE) {
            rejected.add("axes." + name);
            return CardAxis.of(name, AxisStatus.UNKNOWN, null, List.of(), axis.getSource());
        }

        // 값이 있다면서 비어 있으면 화면에 빈 줄이 찍힌다. 모른다고 하는 편이 정직하다.
        if (axis.getStatus() == AxisStatus.FILLED && (value == null || value.isBlank())) {
            rejected.add("axes." + name);
            return CardAxis.of(name, AxisStatus.UNKNOWN, null, List.of(), axis.getSource());
        }

        return axis.copy();
    }
}
