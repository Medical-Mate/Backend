package com.jinryomate.backend.attachment.web;

import com.jinryomate.backend.attachment.dto.AttachmentDtos.AttachmentResponse;
import com.jinryomate.backend.attachment.entity.Attachment;
import com.jinryomate.backend.attachment.service.AttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "사진 첨부", description = "약봉투(S1)와 처방전·안내문(S5) 사진")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @Operation(
            summary = "약봉투 사진 업로드",
            description = """
                    온보딩에서 "이름을 모르면 약 봉투를 찍어도 됩니다"에 해당합니다.

                    `multipart/form-data` 로 보냅니다. 필드명은 `file` 입니다.

                    - 한 장 최대 **10MB**
                    - `jpg` `png` `webp` `heic` `heif` 만 허용 (heic·heif는 아이폰 기본 포맷입니다)
                    - 프로필당 최대 **5장**

                    **온보딩을 먼저 마쳐야 합니다.** 프로필이 없으면 400입니다.

                    응답에 바이트는 담기지 않습니다. `downloadUrl` 로 따로 받아가세요.
                    """)
    @PostMapping(value = "/me/health-profile/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse uploadToProfile(
            @AuthenticationPrincipal Long userId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "MEDICATION_BAG") Attachment.Purpose purpose) {
        return attachmentService.uploadToProfile(userId, purpose, file);
    }

    @Operation(summary = "약봉투 사진 목록")
    @GetMapping("/me/health-profile/attachments")
    public List<AttachmentResponse> listForProfile(@AuthenticationPrincipal Long userId) {
        return attachmentService.listForProfile(userId);
    }

    @Operation(
            summary = "처방전·안내문 업로드",
            description = """
                    "처방전이나 안내문 받으셨으면 사진으로 찍어주세요"에 해당합니다.

                    `purpose` 로 `PRESCRIPTION`(처방전) 또는 `GUIDE`(안내문)를 구분합니다.
                    제한은 약봉투와 같습니다. 기록당 최대 5장입니다.
                    """)
    @PostMapping(value = "/visits/{visitId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AttachmentResponse uploadToVisit(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long visitId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "PRESCRIPTION") Attachment.Purpose purpose) {
        return attachmentService.uploadToVisit(userId, visitId, purpose, file);
    }

    @Operation(summary = "진료 기록의 사진 목록")
    @GetMapping("/visits/{visitId}/attachments")
    public List<AttachmentResponse> listForVisit(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long visitId) {
        return attachmentService.listForVisit(userId, visitId);
    }

    @Operation(
            summary = "사진 내려받기",
            description = "원본 바이트를 그대로 돌려줍니다. 남의 사진은 404입니다.")
    @GetMapping("/attachments/{attachmentId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal Long userId,
                                           @PathVariable Long attachmentId) {
        Attachment attachment = attachmentService.download(userId, attachmentId);

        // inline 으로 두어 앱이 바로 표시할 수 있게 한다.
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(attachment.getOriginalFilename() == null
                        ? "attachment" : attachment.getOriginalFilename())
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .contentLength(attachment.getSizeBytes())
                .body(attachment.getData());
    }

    @Operation(summary = "사진 삭제")
    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Long userId,
                                       @PathVariable Long attachmentId) {
        attachmentService.delete(userId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
