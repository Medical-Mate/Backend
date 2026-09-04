package com.jinryomate.backend.visit.service;

import com.jinryomate.backend.ai.client.AiVisitClient;
import com.jinryomate.backend.ai.dto.ComprehensionQuestion;
import com.jinryomate.backend.ai.dto.GradeResult;
import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardStatus;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.visit.dto.VisitDtos.AnswerResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.Progress;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitResponse;
import com.jinryomate.backend.visit.entity.ComprehensionCheck;
import com.jinryomate.backend.visit.entity.VisitRecord;
import com.jinryomate.backend.visit.repository.ComprehensionCheckRepository;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VisitRecordService {

    private final VisitRecordRepository visitRecordRepository;
    private final ComprehensionCheckRepository checkRepository;
    private final BriefingCardService briefingCardService;
    private final AiVisitClient aiVisitClient;

    /**
     * 진료 직후 기록을 저장하고 되묻기 문항을 만든다.
     *
     * <p>문항은 여기서 한 번에 만든다. 앱이 "3문항 중 2" 진행도를 바로 표시할 수 있고
     * AI 호출도 1회로 끝난다.
     *
     * <p>기록에 아무 항목도 없으면 문항이 0개일 수 있다. 받은 약이 없는데
     * "약은 몇 번 드세요?"를 묻지 않기 위해서다.
     */
    @Transactional
    public VisitResponse create(Long userId, Long cardId, CreateVisitRequest request) {
        BriefingCard card = briefingCardService.findOwned(userId, cardId);

        if (card.getStatus() != CardStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "카드를 먼저 확정해주세요. 진료를 마친 뒤에 남기는 기록입니다.");
        }
        if (visitRecordRepository.existsByCardId(cardId)) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이미 기록이 있습니다.");
        }

        VisitRecord record = VisitRecord.of(card.getUser(), card, request.visitedOn());
        record.applyContent(
                request.clinicName(),
                request.whatWasDone(),
                request.result(),
                request.prescription(),
                request.rawNote());
        visitRecordRepository.save(record);

        // 저장까지 해야 id 가 붙는다. 응답에 checkId 를 담아야 앱이 답변을 보낼 수 있다.
        List<ComprehensionQuestion> questions = aiVisitClient.generateQuestions(record);
        questions.forEach(q -> {
            ComprehensionCheck check = checkRepository.save(
                    ComprehensionCheck.of(record, q.question(), q.expectedAnswer()));
            record.addCheck(check);
        });

        // 진료 내용은 민감정보라 값을 로그에 남기지 않는다.
        log.info("진료 후 기록 저장 userId={} visitId={} 문항={}",
                userId, record.getId(), questions.size());
        return VisitResponse.from(record);
    }

    @Transactional(readOnly = true)
    public VisitResponse get(Long userId, Long visitId) {
        return VisitResponse.from(findOwned(userId, visitId));
    }

    /**
     * 되묻기 답변.
     *
     * <p><b>틀려도 막지 않는다.</b> 시험이 아니라 이해 확인이다. 정정 문구를 돌려주고
     * 앱은 다음 문항으로 넘어간다.
     */
    @Transactional
    public AnswerResponse answer(Long userId, Long visitId, Long checkId, String userAnswer) {
        VisitRecord record = findOwned(userId, visitId);

        ComprehensionCheck check = record.getChecks().stream()
                .filter(c -> checkId.equals(c.getId()))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "문항을 찾을 수 없습니다."));

        if (check.isAnswered()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "이미 답한 문항입니다.");
        }

        GradeResult graded = aiVisitClient.grade(
                check.getQuestion(), check.getExpectedAnswer(), userAnswer);
        check.answer(userAnswer, graded.correct());

        log.info("되묻기 답변 userId={} visitId={} checkId={} correct={}",
                userId, visitId, checkId, graded.correct());

        return new AnswerResponse(
                checkId,
                graded.correct(),
                graded.correction(),
                new Progress(record.answeredCount(), record.getChecks().size()));
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
