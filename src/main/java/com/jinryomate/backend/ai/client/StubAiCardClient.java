package com.jinryomate.backend.ai.client;

import com.jinryomate.backend.ai.dto.AiCardResult;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.card.entity.Medication;
import com.jinryomate.backend.global.web.RequestIdFilter;
import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.HealthProfile;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * AI 서비스가 아직 없어서 쓰는 임시 구현.
 *
 * <p><b>내용은 가짜다.</b> 대화에서 뽑아낸 것이 아니라 세션과 프로필에 있는 값을 옮겨 담을 뿐이다.
 * 그래도 카드 생성 → 검증 → 저장 → 상태 전이 → 버전 관리는 실제 경로로 돌아가므로,
 * 앱이 S3 화면을 만들 수 있고 검증 규칙도 실제로 검증된다.
 *
 * <p>실제 구현체를 붙일 때 이 클래스를 지우면 된다. 인터페이스는 그대로 둔다.
 */
@Component
public class StubAiCardClient implements AiCardClient {

    static final String PIPELINE_VERSION = "stub-0";

    @Override
    public AiCardResult generateCard(IntakeSession session, HealthProfile profile) {
        List<String> answers = session.getMessages().stream()
                .filter(m -> m.getRole() == IntakeMessage.Role.USER)
                .map(IntakeMessage::getText)
                .toList();

        Map<String, List<Integer>> evidence = new LinkedHashMap<>();
        session.getMessages().stream()
                .filter(m -> m.getRole() == IntakeMessage.Role.USER)
                .findFirst()
                .ifPresent(m -> evidence.put("onset", List.of(m.getSeq())));

        CardContent content = new CardContent(
                title(session),
                answers.isEmpty() ? FieldStatus.UNKNOWN : FieldStatus.KNOWN,
                answers.isEmpty() ? null : truncate(answers.get(0), 80),
                FieldStatus.UNKNOWN, null,
                session.getSiteText() == null ? FieldStatus.UNKNOWN : FieldStatus.KNOWN,
                session.getSiteText(),
                List.copyOf(session.getSiteCodes()),
                medicationsStatus(profile), medications(profile),
                allergiesStatus(profile), allergiesText(profile),
                List.of(),
                null,
                evidence);

        return new AiCardResult(content, PIPELINE_VERSION, RequestIdFilter.current());
    }

    private String title(IntakeSession session) {
        String site = session.getSiteText();
        return truncate(site == null || site.isBlank() ? "증상 정리" : site + " 증상", 20);
    }

    // 프로필에 있는 값은 그대로 옮긴다. 세 값 상태를 뭉개지 않는다.
    private FieldStatus medicationsStatus(HealthProfile profile) {
        return profile == null ? FieldStatus.UNKNOWN : profile.getMedicationsStatus();
    }

    private List<Medication> medications(HealthProfile profile) {
        if (profile == null || profile.getMedicationsStatus() != FieldStatus.KNOWN) {
            return List.of();
        }
        List<Medication> result = new ArrayList<>();
        profile.getMedications().forEach(name -> result.add(new Medication(name, null)));
        return result;
    }

    private FieldStatus allergiesStatus(HealthProfile profile) {
        return profile == null ? FieldStatus.UNKNOWN : profile.getAllergiesStatus();
    }

    private String allergiesText(HealthProfile profile) {
        return profile == null ? null : profile.getAllergies();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
