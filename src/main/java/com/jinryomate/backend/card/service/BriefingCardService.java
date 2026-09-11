package com.jinryomate.backend.card.service;

import com.jinryomate.backend.card.dto.CardDtos.AxisEdit;
import com.jinryomate.backend.card.dto.CardDtos.CardResponse;
import com.jinryomate.backend.card.dto.CardDtos.CardSummary;
import com.jinryomate.backend.card.dto.CardDtos.UpdateCardRequest;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.card.repository.BriefingCardRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.service.IntakeSessionService;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingCardService {

    private final BriefingCardRepository cardRepository;
    private final HealthProfileRepository profileRepository;
    private final IntakeSessionService intakeSessionService;
    private final CardAssembler assembler;
    private final CardContentValidator validator;

    /**
     * 목록에 "진료 완료"와 병원명을 붙이는 데만 쓴다.
     *
     * <p>{@code visit} 은 이미 {@code card} 에 기대고 있어서 방향이 하나 더 생긴다.
     * 다만 읽기 전용 조회이고 리포지토리라 생성자 순환이 생기지 않는다.
     */
    private final VisitRecordRepository visitRecordRepository;

    /**
     * 문답을 카드로 만든다.
     *
     * <p><b>AI 를 다시 부르지 않는다.</b> 카드는 매 턴 응답에 딸려 와서 세션에 보관돼 있고,
     * 계약에 카드만 만드는 경로가 없다. 같은 {@code state} 로 다시 불러봐야 바이트까지 같은
     * 카드가 올 뿐이고, 환자에게 질문만 하나 더 나간다.
     *
     * <p><b>여러 번 불러도 카드가 늘지 않는다.</b> 이미 만든 초안이 있으면 그걸 돌려준다.
     * 앱이 화면을 다시 그리거나 네트워크가 끊겼다 이어져도 카드가 쌓이면 안 된다.
     *
     * <p>환자 인적사항은 이 시점 값을 카드에 박아둔다. 프로필을 나중에 고쳐도
     * 이미 만들어진 카드는 그대로 남는다.
     */
    @Transactional
    public CardResponse generate(Long userId, Long sessionId) {
        IntakeSession session = intakeSessionService.findOwned(userId, sessionId);

        BriefingCard existing = cardRepository.findFirstBySessionIdOrderByVersionDesc(sessionId).orElse(null);
        if (existing != null) {
            return CardResponse.from(existing);
        }

        HealthProfile profile = profileRepository.findByUserId(userId).orElse(null);

        CardAssembler.Assembled assembled = assembler.assemble(session.getAiCard(), session.getQuestions());
        CardContentValidator.Result validated = validator.validate(assembled.content());

        BriefingCard card = BriefingCard.draft(session.getUser(), session);
        // 알레르기도 여기서 박는다. 의사에게 보여주는 한 장이 안전 정보를 얻으려고
        // API 를 두 번 부르게 하면 안 된다.
        card.applyPatientSnapshot(
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.age(),
                profile == null ? null : profile.getSex(),
                profile == null ? null : profile.getAllergiesStatus(),
                profile == null ? null : profile.getAllergies());
        card.applyContent(validated.content());
        if (assembled.provenance() != null) {
            card.applyTrace(
                    assembled.provenance().promptVersion(),
                    assembled.provenance().modelId(),
                    assembled.provenance().ontologySnapshot(),
                    null);
        }

        cardRepository.save(card);
        if (session.getStatus() == IntakeSession.Status.IN_PROGRESS) {
            session.complete();
        }

        log.info("카드 생성 userId={} cardId={} prompt={} rejected={}",
                userId, card.getId(), card.getPromptVersion(), validated.rejectedFields());
        return CardResponse.from(card, validated.rejectedFields());
    }

    @Transactional(readOnly = true)
    public CardResponse get(Long userId, Long cardId) {
        return CardResponse.from(findOwned(userId, cardId));
    }

    /**
     * 기록 탭의 "브리핑 카드" 목록 (화면 1j). 최근 작성 순.
     *
     * <p>진료 기록을 카드마다 따로 조회하면 카드 수만큼 쿼리가 나간다(N+1).
     * 이 사용자의 기록을 한 번에 가져와 카드에 붙인다.
     */
    @Transactional(readOnly = true)
    public List<CardSummary> list(Long userId) {
        Map<Long, String> clinicByCardId = new HashMap<>();
        visitRecordRepository.findAllByUserIdOrderByVisitedOnDescIdDesc(userId)
                .forEach(v -> clinicByCardId.put(v.getCard().getId(), v.getClinicName()));

        return cardRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
                // 병원명이 null 이어도 기록은 있을 수 있다. 키가 있는지로 판단한다.
                .map(c -> CardSummary.of(c,
                        clinicByCardId.containsKey(c.getId()),
                        clinicByCardId.get(c.getId())))
                .toList();
    }

    /**
     * 환자가 카드를 고친다. 화면 {@code 1e-1-E}.
     *
     * <p><b>확정된 카드는 고치지 않는다.</b> 대신 이 카드를 이어받은 새 버전을 만들어 거기에
     * 반영한다. 의사가 이미 본 카드가 뒤바뀌면 안 된다.
     *
     * <p>고친 축에는 {@code source} 를 {@code patient_edit} 으로 남기고 근거에
     * {@code "[환자 수정] …"} 을 적는다. <b>의사가 "말한 그대로"와 "나중에 고친 값"을
     * 구별할 수 있어야 한다.</b>
     */
    @Transactional
    public CardResponse update(Long userId, Long cardId, UpdateCardRequest request) {
        BriefingCard card = findOwned(userId, cardId);

        BriefingCard target = card.isEditable() ? card : cardRepository.save(card.newVersion());

        CardContent merged = merge(target, request);
        CardContentValidator.Result validated = validator.validate(merged);
        target.applyContent(validated.content());

        log.info("카드 수정 userId={} cardId={} version={} rejected={}",
                userId, target.getId(), target.getVersion(), validated.rejectedFields());
        return CardResponse.from(target, validated.rejectedFields());
    }

    /** 확정. 이미 확정된 카드를 다시 확정하려 하면 막는다. */
    @Transactional
    public CardResponse confirm(Long userId, Long cardId) {
        BriefingCard card = findOwned(userId, cardId);
        if (!card.isEditable()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이미 확정된 카드입니다.");
        }
        card.confirm();
        log.info("카드 확정 userId={} cardId={} version={}", userId, cardId, card.getVersion());
        return CardResponse.from(card);
    }

    /** 남의 카드는 존재 자체를 알려주지 않는다. */
    @Transactional(readOnly = true)
    public BriefingCard findOwned(Long userId, Long cardId) {
        BriefingCard card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없습니다."));
        if (!card.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "카드를 찾을 수 없습니다.");
        }
        return card;
    }

    /** 보낸 것만 갈아끼우고 나머지는 카드에 있던 값을 그대로 쓴다. */
    private CardContent merge(BriefingCard card, UpdateCardRequest request) {
        Map<String, CardAxis> axes = new LinkedHashMap<>(card.axesByName());

        if (request.axes() != null) {
            for (AxisEdit edit : request.axes()) {
                CardAxis current = axes.get(edit.axis());
                if (current == null) {
                    // 카드에 없는 축은 만들지 않는다. 화면에 없는 것을 고칠 수는 없다.
                    throw new ApiException(ErrorCode.INVALID_REQUEST,
                            "카드에 없는 항목입니다: " + edit.axis());
                }
                axes.put(edit.axis(), current.editedByPatient(edit.value()));
            }
        }

        return new CardContent(
                // 제목은 AI 가 부위 + 기간을 조합해 만든다. 환자가 고치는 자리가 아니다.
                card.getTitle(),
                request.chiefComplaint() == null ? card.getChiefComplaint() : request.chiefComplaint(),
                axes,
                List.copyOf(card.getRedFlags()),
                request.patientNotes() == null
                        ? List.copyOf(card.getPatientNotes()) : request.patientNotes(),
                request.questions() == null ? List.copyOf(card.getQuestions()) : request.questions(),
                List.copyOf(card.getDepartmentGuidance()),
                card.getDepartmentGuidanceSource(),
                card.getCompleteness(),
                card.getMinimallyComplete());
    }
}
