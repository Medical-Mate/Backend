package com.jinryomate.backend.handoff.service;

import com.jinryomate.backend.card.entity.BriefingCard;
import com.jinryomate.backend.card.service.BriefingCardService;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.handoff.config.HandoffProperties;
import com.jinryomate.backend.handoff.dto.HandoffDtos.HandoffView;
import com.jinryomate.backend.handoff.dto.HandoffDtos.ShareLinkResponse;
import com.jinryomate.backend.handoff.entity.ShareLink;
import com.jinryomate.backend.handoff.repository.ShareLinkRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 진료실 전달(S4)과 공유 링크(S6).
 *
 * <p>QR 은 MVP 에서 뺐다. 환자가 자기 화면을 의사에게 보여주는 형식이라
 * 중간에 이미지를 낄 자리가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HandoffService {

    private final BriefingCardService briefingCardService;
    private final ShareLinkRepository shareLinkRepository;
    private final ShareTokenGenerator tokenGenerator;
    private final HandoffProperties properties;

    /**
     * 의사에게 보여줄 카드를 연다 (S4).
     *
     * <p>여는 순간을 전달 시각으로 남긴다. 이후 "진료 어떠셨어요"를 물을 근거가 된다.
     */
    @Transactional
    public HandoffView open(Long userId, Long cardId) {
        BriefingCard card = requireHandoffReady(briefingCardService.findOwned(userId, cardId));
        card.markHandedOff();

        log.info("진료실 전달 userId={} cardId={}", userId, cardId);
        return HandoffView.from(card);
    }

    // ---------- 공유 링크 ----------

    /**
     * 공유 링크 발급 (S6).
     *
     * <p>발급할 때마다 새 토큰을 만든다. 기존 링크를 재사용하면 예전에 공유한 주소의
     * 수명이 계속 늘어난다.
     */
    @Transactional
    public ShareLinkResponse issueShareLink(Long userId, Long cardId) {
        BriefingCard card = requireHandoffReady(briefingCardService.findOwned(userId, cardId));

        ShareLink link = shareLinkRepository.save(ShareLink.issue(
                card.getUser(),
                card,
                tokenGenerator.generate(),
                Instant.now().plus(properties.shareTtl())));

        // 토큰은 그 자체가 열쇠라 로그에 남기지 않는다.
        log.info("공유 링크 발급 userId={} cardId={} shareLinkId={} expiresAt={}",
                userId, cardId, link.getId(), link.getExpiresAt());
        return ShareLinkResponse.of(link, properties.publicBaseUrl());
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResponse> listShareLinks(Long userId) {
        return shareLinkRepository.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(link -> ShareLinkResponse.of(link, properties.publicBaseUrl()))
                .toList();
    }

    @Transactional
    public void revoke(Long userId, Long shareLinkId) {
        ShareLink link = shareLinkRepository.findById(shareLinkId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "링크를 찾을 수 없습니다."));
        // 남의 링크는 존재 자체를 알려주지 않는다.
        if (!link.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "링크를 찾을 수 없습니다.");
        }

        link.revoke();
        log.info("공유 링크 폐기 userId={} shareLinkId={}", userId, shareLinkId);
    }

    /**
     * 인증 없이 링크를 연다.
     *
     * <p>폐기·만료·없음을 <b>모두 404 로 묶는다</b>. "만료됐습니다"라고 알려주면
     * 그 토큰이 존재했다는 사실이 새어 나가고, 무작위 대입의 성공 여부를 알려주는 셈이 된다.
     */
    @Transactional
    public HandoffView openShared(String token) {
        ShareLink link = shareLinkRepository.findByToken(token)
                .filter(l -> l.isOpenable(Instant.now()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "링크를 열 수 없습니다. 만료되었거나 삭제된 링크입니다."));

        link.recordView();
        log.info("공유 링크 열람 shareLinkId={} viewCount={}", link.getId(), link.getViewCount());
        return HandoffView.from(link.getCard());
    }

    private BriefingCard requireHandoffReady(BriefingCard card) {
        if (!card.isHandoffReady()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "카드를 먼저 확정해주세요. 확정한 카드만 보여줄 수 있습니다.");
        }
        return card;
    }
}
