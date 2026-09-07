package com.jinryomate.backend.visit.service;

import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.entity.CardStatus;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitResponse;
import com.jinryomate.backend.visit.dto.VisitDtos.VisitSummary;
import com.jinryomate.backend.visit.entity.VisitRecord;
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
    private final BriefingCardService briefingCardService;

    /**
     * 진료 직후 기록.
     *
     * <p>확정한 카드에만, 카드 하나에 기록 하나다.
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

        // 진료 내용은 민감정보라 값을 로그에 남기지 않는다.
        log.info("진료 후 기록 저장 userId={} visitId={}", userId, record.getId());
        return VisitResponse.from(record);
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
