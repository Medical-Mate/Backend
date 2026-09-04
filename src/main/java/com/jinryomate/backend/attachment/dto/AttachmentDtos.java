package com.jinryomate.backend.attachment.dto;

import com.jinryomate.backend.attachment.entity.Attachment;
import java.time.Instant;

/** 첨부의 응답. 업로드 요청은 multipart 라 DTO 가 없다. */
public final class AttachmentDtos {

    private AttachmentDtos() {}

    /**
     * 업로드 결과. <b>바이트는 담지 않는다.</b> 내려받기는 {@code downloadUrl} 로 따로 한다.
     */
    public record AttachmentResponse(
            Long attachmentId,
            Attachment.Purpose purpose,
            String contentType,
            String originalFilename,
            long sizeBytes,
            String downloadUrl,
            Instant createdAt
    ) {
        public static AttachmentResponse from(Attachment a) {
            return new AttachmentResponse(
                    a.getId(),
                    a.getPurpose(),
                    a.getContentType(),
                    a.getOriginalFilename(),
                    a.getSizeBytes(),
                    "/api/attachments/" + a.getId(),
                    a.getCreatedAt());
        }
    }
}
