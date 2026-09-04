package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.ComprehensionQuestion;
import com.jinryomate.backend.ai.dto.GradeResult;
import com.jinryomate.backend.visit.entity.VisitRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * AI 서비스가 아직 없어서 쓰는 임시 구현.
 *
 * <p>문항은 기록에 실제로 있는 항목에서만 만든다. 받은 약이 없는데 "약은 몇 번 드세요?"를
 * 물으면 환자가 당황한다. 채점은 정답 문구의 핵심 낱말이 답에 들어 있는지로 대신한다 —
 * 실제 AI라면 "어... 한 번인가?" 같은 답도 읽어낼 것이다.
 *
 * <p>실제 구현체를 붙일 때 이 클래스를 지우면 된다. 인터페이스는 그대로 둔다.
 */
@Component
public class StubAiVisitClient implements AiVisitClient {

    /** 와이어프레임 기준 "질문 3개 이내". */
    static final int MAX_QUESTIONS = 3;

    @Override
    public List<ComprehensionQuestion> generateQuestions(VisitRecord record) {
        List<ComprehensionQuestion> questions = new ArrayList<>();

        if (hasText(record.getPrescription())) {
            questions.add(new ComprehensionQuestion(
                    "약은 어떻게 드시면 되죠?", record.getPrescription()));
        }
        if (hasText(record.getResult())) {
            questions.add(new ComprehensionQuestion(
                    "결과는 언제 확인하면 되나요?", record.getResult()));
        }
        if (hasText(record.getWhatWasDone())) {
            questions.add(new ComprehensionQuestion(
                    "오늘 병원에서 무엇을 하셨죠?", record.getWhatWasDone()));
        }

        return questions.size() > MAX_QUESTIONS
                ? List.copyOf(questions.subList(0, MAX_QUESTIONS))
                : List.copyOf(questions);
    }

    @Override
    public GradeResult grade(String question, String expectedAnswer, String userAnswer) {
        if (!hasText(userAnswer)) {
            return new GradeResult(false, expectedAnswer);
        }
        boolean correct = containsAnyKeyword(expectedAnswer, userAnswer);
        return new GradeResult(correct, correct ? "맞아요." : expectedAnswer);
    }

    /** 정답 문구를 낱말로 쪼개 하나라도 겹치면 맞은 것으로 본다. */
    private boolean containsAnyKeyword(String expectedAnswer, String userAnswer) {
        String answer = userAnswer.toLowerCase(Locale.KOREAN);
        for (String token : expectedAnswer.toLowerCase(Locale.KOREAN).split("[\\s·,.]+")) {
            if (token.length() >= 2 && answer.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
