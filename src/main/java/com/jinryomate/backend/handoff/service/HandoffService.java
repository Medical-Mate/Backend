package com.jinryomate.backend.handoff.service;

import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.handoff.dto.HandoffDtos.HandoffView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진료실 전달 (화면 1f).
 *
 * <p>환자가 자기 화면을 의사에게 보여준다. QR 도 공유 링크도 쓰지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffService {

    private final BriefingCardService briefingCardService;

    /**
     * 의사에게 보여줄 카드를 연다.
     *
     * <p>여는 순간을 전달 시각으로 남긴다. 이후 "진료 어떠셨어요"를 물을 근거가 된다.
     */
    @Transactional
    public HandoffView open(Long userId, Long cardId) {
        BriefingCard card = briefingCardService.findOwned(userId, cardId);

        if (!card.isHandoffReady()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "카드를 먼저 확정해주세요. 확정한 카드만 보여줄 수 있습니다.");
        }
        card.markHandedOff();

        log.info("진료실 전달 userId={} cardId={}", userId, cardId);
        return HandoffView.from(card);
    }
}
