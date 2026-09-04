package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.ComprehensionQuestion;
import com.jinryomate.backend.ai.dto.GradeResult;
import com.jinryomate.backend.visit.entity.VisitRecord;
import java.util.List;

/**
 * 진료 후 기록에서 되묻기 문항을 만들고 답을 채점하는 AI 호출.
 *
 * <p>채점을 AI가 하는 이유는 환자가 "어... 한 번인가?" 처럼 답하기 때문이다.
 * 문자열 비교로는 맞고 틀림을 가릴 수 없다.
 *
 * <p>지금은 {@link StubAiVisitClient} 가 구현한다. AI 서비스가 붙으면 구현체만 갈아끼운다.
 */
public interface AiVisitClient {

    /** 기록을 저장할 때 한 번에 만든다. 앱이 "3문항 중 2" 진행도를 바로 표시할 수 있도록. */
    List<ComprehensionQuestion> generateQuestions(VisitRecord record);

    GradeResult grade(String question, String expectedAnswer, String userAnswer);
}
