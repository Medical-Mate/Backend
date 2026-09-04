package com.jinryomate.backend.attachment.service;

import com.jinryomate.backend.attachment.dto.AttachmentDtos.AttachmentResponse;
import com.jinryomate.backend.attachment.entity.Attachment;
import com.jinryomate.backend.attachment.repository.AttachmentRepository;
import com.jinryomate.backend.global.error.ApiException;
import com.jinryomate.backend.global.error.ErrorCode;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import com.jinryomate.backend.visit.entity.VisitRecord;
import com.jinryomate.backend.visit.service.VisitRecordService;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    /** 한 장 최대 10MB. DB에 담으므로 제한이 곧 방어선이다. */
    static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;

    /** 소유 대상당 최대 장수. 약봉투도 처방전도 몇 장이면 충분하다. */
    static final int MAX_COUNT_PER_OWNER = 5;

    /** heic·heif 는 아이폰 기본 포맷이라 빠뜨리면 절반이 못 올린다. */
    static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/heic", "image/heif");

    private final AttachmentRepository attachmentRepository;
    private final HealthProfileRepository profileRepository;
    private final VisitRecordService visitRecordService;

    /** 약봉투 사진 (S1). 프로필이 없으면 만들 수 없다. */
    @Transactional
    public AttachmentResponse uploadToProfile(Long userId, Attachment.Purpose purpose, MultipartFile file) {
        HealthProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REQUEST,
                        "온보딩을 먼저 진행해주세요."));

        validate(file, attachmentRepository.countByProfileId(profile.getId()));

        Attachment saved = attachmentRepository.save(Attachment.forProfile(
                profile, purpose, file.getContentType(),
                file.getOriginalFilename(), readBytes(file)));

        // 파일명에 환자 정보가 들어갈 수 있어 로그에 남기지 않는다.
        log.info("첨부 업로드 userId={} attachmentId={} purpose={} size={}",
                userId, saved.getId(), purpose, saved.getSizeBytes());
        return AttachmentResponse.from(saved);
    }

    /** 처방전·안내문 (S5). */
    @Transactional
    public AttachmentResponse uploadToVisit(Long userId, Long visitId,
                                            Attachment.Purpose purpose, MultipartFile file) {
        VisitRecord record = visitRecordService.findOwned(userId, visitId);

        validate(file, attachmentRepository.countByVisitRecordId(record.getId()));

        Attachment saved = attachmentRepository.save(Attachment.forVisit(
                record, purpose, file.getContentType(),
                file.getOriginalFilename(), readBytes(file)));

        log.info("첨부 업로드 userId={} visitId={} attachmentId={} purpose={} size={}",
                userId, visitId, saved.getId(), purpose, saved.getSizeBytes());
        return AttachmentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listForProfile(Long userId) {
        return profileRepository.findByUserId(userId)
                .map(p -> attachmentRepository.findAllByProfileId(p.getId()).stream()
                        .map(AttachmentResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listForVisit(Long userId, Long visitId) {
        VisitRecord record = visitRecordService.findOwned(userId, visitId);
        return attachmentRepository.findAllByVisitRecordId(record.getId()).stream()
                .map(AttachmentResponse::from)
                .toList();
    }

    /** 내려받기. 바이트를 실제로 읽는 유일한 경로다. */
    @Transactional(readOnly = true)
    public Attachment download(Long userId, Long attachmentId) {
        return findOwned(userId, attachmentId);
    }

    @Transactional
    public void delete(Long userId, Long attachmentId) {
        attachmentRepository.delete(findOwned(userId, attachmentId));
        log.info("첨부 삭제 userId={} attachmentId={}", userId, attachmentId);
    }

    private Attachment findOwned(Long userId, Long attachmentId) {
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없습니다."));
        // 남의 첨부는 존재 자체를 알려주지 않는다.
        if (!attachment.isOwnedBy(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "사진을 찾을 수 없습니다.");
        }
        return attachment;
    }

    private void validate(MultipartFile file, long currentCount) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사진이 비어 있습니다.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "사진은 한 장에 10MB까지 올릴 수 있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "사진 파일만 올릴 수 있습니다. (jpg, png, webp, heic)");
        }
        if (currentCount >= MAX_COUNT_PER_OWNER) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "사진은 " + MAX_COUNT_PER_OWNER + "장까지 올릴 수 있습니다.");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            // 예외 메시지에 경로가 들어갈 수 있어 그대로 노출하지 않는다.
            log.warn("첨부 읽기 실패: {}", e.getClass().getSimpleName());
            throw new ApiException(ErrorCode.INVALID_REQUEST, "사진을 읽지 못했습니다. 다시 시도해주세요.");
        }
    }
}
