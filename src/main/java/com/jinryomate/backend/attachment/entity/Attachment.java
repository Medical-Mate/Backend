package com.jinryomate.backend.attachment.entity;

import com.jinryomate.backend.auth.entity.User;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.visit.entity.VisitRecord;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사진 첨부. 약봉투(S1)와 처방전·안내문(S5).
 *
 * <p>바이트를 PostgreSQL {@code bytea} 에 그대로 담는다. 배포 대상이 이미 백엔드·AI 둘인데
 * 스토리지까지 셋이 되는 것을 피한 결과다.
 *
 * <p>소유는 <b>nullable FK 두 개</b>로 표현한다. 둘 중 정확히 하나만 채워진다.
 * 다형성({@code owner_type} + {@code owner_id})으로 하면 FK 를 걸 수 없어,
 * 원본이 지워져도 첨부가 남을 수 있다. 진료 데이터라 그게 안 된다.
 */
@Entity
@Table(name = "attachments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment {

    public enum Purpose {
        /** 약봉투. 이름을 모를 때 찍어 올린다 (S1). */
        MEDICATION_BAG,
        /** 처방전 (S5). */
        PRESCRIPTION,
        /** 안내문 (S5). */
        GUIDE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 접근 확인용. 첨부를 조회할 때마다 소유자를 타고 올라가지 않아도 되도록 둔다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id")
    private HealthProfile profile;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "visit_record_id")
    private VisitRecord visitRecord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Purpose purpose;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(length = 255)
    private String originalFilename;

    @Column(nullable = false)
    private long sizeBytes;

    /**
     * 사진 원본.
     *
     * <p>지연 로딩으로 둔다. 목록을 조회할 때마다 바이트를 통째로 읽으면 메모리가 터진다.
     */
    @Lob
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    private Attachment(User user, Purpose purpose, String contentType,
                       String originalFilename, byte[] data) {
        this.user = user;
        this.purpose = purpose;
        this.contentType = contentType;
        this.originalFilename = originalFilename;
        this.data = data;
        this.sizeBytes = data.length;
    }

    public static Attachment forProfile(HealthProfile profile, Purpose purpose,
                                        String contentType, String originalFilename, byte[] data) {
        Attachment attachment = new Attachment(
                profile.getUser(), purpose, contentType, originalFilename, data);
        attachment.profile = profile;
        return attachment;
    }

    public static Attachment forVisit(VisitRecord visitRecord, Purpose purpose,
                                      String contentType, String originalFilename, byte[] data) {
        Attachment attachment = new Attachment(
                visitRecord.getUser(), purpose, contentType, originalFilename, data);
        attachment.visitRecord = visitRecord;
        return attachment;
    }

    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }
}
