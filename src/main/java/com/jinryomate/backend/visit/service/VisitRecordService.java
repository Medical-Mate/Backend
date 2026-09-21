package com.jinryomate.backend.visit.service;

import com.jinryomate.backend.ai.client.AiMemoClient;
import com.jinryomate.backend.ai.dto.FollowUp;
import com.jinryomate.backend.ai.dto.LabelsMeta;
import com.jinryomate.backend.ai.dto.MemoClassification;
import com.jinryomate.backend.ai.dto.MemoRequest;
import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.card.dto.CardDtos.Axis;
import com.jinryomate.backend.card.entity.AxisSource;
import com.jinryomate.backend.card.entity.AxisStatus;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardAxis;
import com.jinryomate.backend.card.entity.CardStatus;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.ClassifyMemoResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.FollowUpRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.LabelsMetaRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.UpdateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitAxisRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitSummary;
import com.jinryomate.backend.visit.entity.VisitMemoAudit;
import com.jinryomate.backend.visit.entity.VisitRecord;
import com.jinryomate.backend.visit.repository.VisitMemoAuditRepository;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
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
public class VisitRecordService {

    private final VisitRecordRepository visitRecordRepository;
    private final VisitMemoAuditRepository visitMemoAuditRepository;
    private final UserRepository userRepository;
    private final BriefingCardService briefingCardService;
    private final AiMemoClient aiMemoClient;

    /**
     * 진료 직후 기록.
     *
     * <p>확정한 카드에만 남긴다. <b>카드 하나에 기록이 여럿일 수 있다.</b>
     *
     * <p>같은 증상으로 다시 가는 것이 진료의 보통 모양이다. 첫 진료에서 검사를 받고
     * 두 번째에 결과를 듣는데, 그때 들은 말은 첫 기록을 덮어쓸 것이 아니라 따로 쌓여야
     * 한다. 시안 {@code 1j-3-R} 이 그 화면이다.
     *
     * <p><b>같은 날 두 건도 받는다.</b> 하루에 두 병원에 가는 일이 있고, 막으면 그때
     * 적을 자리가 없다. 실수로 두 번 저장되는 것은 화면이 막을 일이다 — 여기서 날짜로
     * 막으면 정상 경로가 함께 막힌다.
     */
    @Transactional
    public VisitResponse create(Long userId, Long cardId, CreateVisitRequest request) {
        BriefingCard card = briefingCardService.findOwned(userId, cardId);

        if (card.getStatus() != CardStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "카드를 먼저 확정해주세요. 진료를 마친 뒤에 남기는 기록입니다.");
        }

        VisitRecord record = VisitRecord.of(card.getUser(), card, request.visitedOn());
        record.applyContent(
                request.clinicName(),
                toFollowUp(request.followUp()),
                request.patientNotes(),
                request.rawNote());
        record.applyAxes(toAxes(request.axes()));
        record.applyExtractedBy(toLabelsMeta(request.extractedBy()));
        visitRecordRepository.save(record);

        // 진료 내용은 민감정보라 값을 로그에 남기지 않는다.
        log.info("진료 후 기록 저장 userId={} visitId={} 항목={}",
                userId, record.getId(), record.getAxes().size());
        return VisitResponse.from(record);
    }

    /**
     * 기록을 고친다. 화면 {@code 1q-1-E}.
     *
     * <p><b>확정 개념이 없다.</b> 카드와 다르다 — 카드는 의사가 본 것이라 뒤바뀌면 안 되지만,
     * 이 기록은 환자 본인이 보는 메모다. 잘못 적은 것을 못 고치게 할 이유가 없다.
     */
    @Transactional
    public VisitResponse update(Long userId, Long visitId, UpdateVisitRequest request) {
        VisitRecord record = findOwned(userId, visitId);

        record.changeVisitedOn(request.visitedOn());
        record.applyContent(
                request.clinicName() != null ? request.clinicName() : record.getClinicName(),
                request.followUp() != null ? toFollowUp(request.followUp()) : record.followUp(),
                request.patientNotes() != null ? request.patientNotes() : record.getPatientNotes(),
                request.rawNote() != null ? request.rawNote() : record.getRawNote());
        record.applyAxes(toAxes(request.axes()));
        record.applyExtractedBy(toLabelsMeta(request.extractedBy()));

        log.info("진료 후 기록 수정 userId={} visitId={}", userId, visitId);
        return VisitResponse.from(record);
    }

    /**
     * 메모를 항목으로 나눈다. 화면 {@code 1p} 의 "AI로 정리하기".
     *
     * <p><b>결과를 저장하지 않는다.</b> 환자가 나눈 결과를 고치고 나서 저장하기 때문이다.
     *
     * <p>카드에 매이지 않는다 — 1p 는 병원을 고른 직후 화면이고, 그 시점에 어느 카드에
     * 붙일지는 아직 앱만 안다.
     *
     * <p><b>감사 기록은 남긴다.</b> AI 회귀 eval 재료다(Medical-Mate/AI#113). 결과와
     * 달리 이것은 환자가 저장을 누르지 않아도 남는다 — {@link VisitMemoAudit} 에 그
     * 이유와 지우는 날을 적어 두었다.
     *
     * <p><b>여기에 {@code @Transactional} 을 붙이지 않는다.</b> 붙이면 감사 저장이
     * 실패했을 때 트랜잭션이 롤백 표시를 달고, 잡아서 넘어가도 바깥 커밋이
     * {@code UnexpectedRollbackException} 으로 터진다 — 삼키려던 500 이 그대로
     * 되살아난다. 트랜잭션 없이 두면 저장 한 번이 제 트랜잭션을 갖는다.
     */
    public ClassifyMemoResponse classify(Long userId, ClassifyMemoRequest request) {
        MemoRequest toAi = new MemoRequest(
                request.memo(), request.visitedOn(), request.clinicName(),
                request.labels(), request.classify(), toLabelsMeta(request.labelsMeta()),
                request.splitVersion());

        MemoClassification result = aiMemoClient.classify(toAi);
        rememberAudit(userId, result);

        Map<String, Axis> axes = new LinkedHashMap<>();
        result.axes().forEach((name, a) -> axes.put(name, Axis.from(a)));

        // 메모 본문은 민감정보라 남기지 않는다. 몇 줄로 나뉘었는지와 모델을 썼는지만 남긴다.
        log.info("메모 분류 userId={} 문장={} 항목={} 모델호출={} split={}",
                userId, result.sentences().size(), axes.size(), toAi.effectiveClassify(),
                result.splitVersion());

        return new ClassifyMemoResponse(
                axes,
                result.sentences(),
                result.labels(),
                result.patientNotes(),
                result.followUp(),
                new LabelsMeta(result.modelId(), result.promptVersion()),
                result.splitVersion());
    }

    /**
     * AI 가 무엇을 보고 무엇을 뱉었는지 남긴다.
     *
     * <p><b>실패해도 넘어간다.</b> 감사 행 하나를 못 써서 환자가 방금 적은 메모 분류가
     * 500 이 되면 안 된다 — {@code HttpAiMemoClient} 가 "모르는 값이 와도 막지 않는다"
     * 로 잡아 둔 것과 같은 저울이다. 대신 예외 종류를 남겨, 켜 놓고 안 걸려 있는 상태를
     * 로그로 알아챌 수 있게 한다.
     *
     * <p>계약이 {@code audit} 을 안 주면(예전 이미지) 조용히 넘어간다.
     */
    private void rememberAudit(Long userId, MemoClassification result) {
        if (result.audit() == null || result.audit().isBlank()) {
            return;
        }
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "다시 로그인해주세요."));
            visitMemoAuditRepository.save(VisitMemoAudit.of(user, result.source(), result.audit()));
        } catch (RuntimeException e) {
            // 메모 본문은 민감정보라 예외 메시지를 남기지 않는다. 종류만 남긴다.
            log.warn("메모 감사 기록을 못 남겼습니다 userId={} 예외={}", userId, e.getClass().getSimpleName());
        }
    }

    /** 요청의 폰 모델 정보를 AI 쪽 값으로 옮긴다. 안 보냈으면 null 이다. */
    private LabelsMeta toLabelsMeta(LabelsMetaRequest requested) {
        if (requested == null) {
            return null;
        }
        LabelsMeta meta = new LabelsMeta(requested.modelId(), requested.promptVersion());
        return meta.isEmpty() ? null : meta;
    }

    /**
     * 요청의 재방문 시점을 저장할 값으로 옮긴다.
     *
     * <p>안이 비어 있으면 {@link FollowUp#NONE} 이다 — 앱이 빈 객체를 보내는 것과 필드를
     * 생략하는 것이 같은 뜻이어야 한다.
     */
    private FollowUp toFollowUp(FollowUpRequest requested) {
        if (requested == null) {
            return FollowUp.NONE;
        }
        FollowUp followUp = new FollowUp(
                requested.date(), requested.text(), requested.approximate());
        return followUp.isEmpty() ? FollowUp.NONE : followUp;
    }

    /**
     * 요청의 항목을 저장할 축으로 옮긴다.
     *
     * <p><b>출처는 {@code PATIENT_EDIT} 로 박는다.</b> 앱이 보낸 값은 환자가 화면에서 보고
     * 넘긴 것이다 — AI 가 나눈 그대로였을 수도 있지만 그걸 우리가 구별할 방법이 없고,
     * "AI 가 뽑았다"고 잘못 적는 쪽이 의사 화면에서 더 나쁘다.
     */
    private List<CardAxis> toAxes(List<VisitAxisRequest> requested) {
        if (requested == null) {
            return null;
        }
        return requested.stream()
                .map(a -> CardAxis.of(
                        a.axis(),
                        a.value() == null || a.value().isBlank() ? AxisStatus.UNKNOWN : AxisStatus.FILLED,
                        a.value(),
                        a.value() == null || a.value().isBlank() ? List.of() : List.of(a.value()),
                        AxisSource.PATIENT_EDIT))
                .toList();
    }

    @Transactional(readOnly = true)
    public VisitResponse get(Long userId, Long visitId) {
        return VisitResponse.from(findOwned(userId, visitId));
    }

    /** 기록 탭의 "진료 기록" 목록 (화면 1j). 최근 진료일 순. */
    @Transactional(readOnly = true)
    public List<VisitSummary> list(Long userId) {
        return visitRecordRepository.findAllByUserIdOrderByVisitedOnDescIdDesc(userId).stream()
                .map(VisitSummary::from)
                .toList();
    }

    /**
     * 이 카드로 다녀온 진료 전부. 화면 {@code 1j-3-R} 의 "진료 2회".
     *
     * <p><b>버전을 가리지 않고 모은다.</b> 재방문 전에 카드를 고치면 버전이 올라가서 첫
     * 기록과 두 번째 기록이 서로 다른 행에 붙는다. 앱이 {@code cardId} 로 목록을 걸러
     * 모으면 그때 한쪽이 빠지고, 게다가 목록이 주는 {@code cardId} 는 <b>최신 버전 id</b>
     * 라 고칠 때마다 바뀐다 — 앱이 묶을 안정된 열쇠가 없다.
     *
     * <p>그래서 체인의 <b>아무 카드 id</b> 나 주면 그 문답의 기록을 전부 돌려준다.
     */
    @Transactional(readOnly = true)
    public List<VisitSummary> listByCard(Long userId, Long cardId) {
        BriefingCard card = briefingCardService.findOwned(userId, cardId);
        return visitRecordRepository.findAllBySessionId(card.getSession().getId()).stream()
                .map(VisitSummary::from)
                .toList();
    }

    /**
     * 진료 기록을 지운다. 기록 목록(화면 {@code 1j})의 삭제가 이 경로다.
     *
     * <p><b>카드는 건드리지 않는다.</b> 전에는 이 자리가 없어서 기록 삭제가 카드 삭제로
     * 대신 나갔고, 기록 한 건을 지우려던 사용자가 카드까지 잃었다.
     */
    @Transactional
    public void delete(Long userId, Long visitId) {
        VisitRecord visit = findOwned(userId, visitId);
        visitRecordRepository.delete(visit);
        log.info("진료 기록 삭제 userId={} visitId={}", userId, visitId);
    }

    /** 남의 기록은 존재 자체를 알려주지 않는다. */
    @Transactional(readOnly = true)
    public VisitRecord findOwned(Long userId, Long visitId) {
        VisitRecord record = visitRecordRepository.findById(visitId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없습니다."));
        if (!record.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "기록을 찾을 수 없습니다.");
        }
        return record;
    }
}
