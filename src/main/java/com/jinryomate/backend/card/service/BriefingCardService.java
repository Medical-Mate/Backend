package com.jinryomate.backend.card.service;

import com.jinryomate.backend.ai.client.AiCardClient;
import com.jinryomate.backend.ai.dto.AiCardResult;
import com.jinryomate.backend.card.dto.CardDtos.CardResponse;
import com.jinryomate.backend.card.dto.CardDtos.CardSummary;
import com.jinryomate.backend.card.dto.CardDtos.TextFieldRequest;
import com.jinryomate.backend.card.dto.CardDtos.UpdateCardRequest;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardContent;
import com.jinryomate.backend.card.repository.BriefingCardRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.service.IntakeSessionService;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.util.HashMap;
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
    private final AiCardClient aiCardClient;
    private final CardContentValidator validator;

    /**
     * 목록에 "진료 완료"와 병원명을 붙이는 데만 쓴다.
     *
     * <p>{@code visit} 은 이미 {@code card} 에 기대고 있어서 방향이 하나 더 생긴다.
     * 다만 읽기 전용 조회이고 리포지토리라 생성자 순환이 생기지 않는다.
     * 홈 요약(화면 1n)도 같은 조합을 필요로 하므로, 그때 조합 전용 자리로 옮길지 다시 본다.
     */
    private final VisitRecordRepository visitRecordRepository;

    /**
     * 문답을 카드로 만든다.
     *
     * <p>검증에 걸린 필드는 {@code UNKNOWN}으로 낮춰 저장하고, 어느 필드가 걸렸는지 응답에 담는다.
     * <b>카드 생성이 통째로 실패해 문답이 날아가는 일은 없어야 한다.</b>
     *
     * <p>환자 인적사항은 이 시점 값을 카드에 박아둔다. 프로필을 나중에 고쳐도
     * 이미 만들어진 카드는 그대로 남는다.
     */
    @Transactional
    public CardResponse generate(Long userId, Long sessionId) {
        IntakeSession session = intakeSessionService.findOwned(userId, sessionId);
        HealthProfile profile = profileRepository.findByUserId(userId).orElse(null);

        AiCardResult result = aiCardClient.generateCard(session, profile);
        CardContentValidator.Result validated = validator.validate(result.content());

        BriefingCard card = BriefingCard.draft(session.getUser(), session);
        card.applyPatientSnapshot(
                profile == null ? null : profile.getName(),
                profile == null ? null : profile.age(),
                profile == null ? null : profile.getSex());
        card.applyContent(validated.content());
        card.applyTrace(result.pipelineVersion(), result.requestId());

        cardRepository.save(card);
        if (session.getStatus() == IntakeSession.Status.IN_PROGRESS) {
            session.complete();
        }

        log.info("카드 생성 userId={} cardId={} pipeline={} rejected={}",
                userId, card.getId(), result.pipelineVersion(), validated.rejectedFields());
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
     *
     * <p>월별 그룹({@code 2026년 9월})은 앱이 묶는다. 서버가 그룹까지 만들면 응답이 화면에
     * 묶여, 홈 화면처럼 "최근 3건"만 쓰는 곳에서 재사용할 수 없다.
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
     * 환자가 카드를 고친다.
     *
     * <p><b>확정된 카드는 고치지 않는다.</b> 대신 이 카드를 이어받은 새 버전을 만들어 거기에 반영한다.
     * 의사가 이미 본 카드가 뒤바뀌면 안 된다.
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

    /** 보낸 필드만 갈아끼우고 나머지는 카드에 있던 값을 그대로 쓴다. */
    private CardContent merge(BriefingCard card, UpdateCardRequest request) {
        return new CardContent(
                request.title() == null ? card.getTitle() : request.title(),
                statusOf(request.onset(), card.getOnsetStatus()),
                textOf(request.onset(), card.getOnsetText()),
                statusOf(request.pattern(), card.getPatternStatus()),
                textOf(request.pattern(), card.getPatternText()),
                card.getSiteStatus(),
                card.getSiteText(),
                List.copyOf(card.getSiteCodes()),
                card.getMedicationsStatus(),
                List.copyOf(card.getMedications()),
                statusOf(request.allergies(), card.getAllergiesStatus()),
                textOf(request.allergies(), card.getAllergiesText()),
                request.questions() == null ? List.copyOf(card.getQuestions()) : request.questions(),
                request.suggestedDepartment() == null
                        ? card.getSuggestedDepartment() : request.suggestedDepartment(),
                card.getEvidence());
    }

    private FieldStatus statusOf(TextFieldRequest field, FieldStatus fallback) {
        return field == null ? fallback : field.status();
    }

    private String textOf(TextFieldRequest field, String fallback) {
        return field == null ? fallback : field.text();
    }
}
