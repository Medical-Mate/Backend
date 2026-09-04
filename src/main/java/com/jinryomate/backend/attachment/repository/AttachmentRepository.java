package com.jinryomate.backend.attachment.repository;

import com.jinryomate.backend.attachment.entity.Attachment;
import com.jinryomate.backend.auth.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findAllByProfileId(Long profileId);

    List<Attachment> findAllByVisitRecordId(Long visitRecordId);

    long countByProfileId(Long profileId);

    long countByVisitRecordId(Long visitRecordId);

    void deleteAllByUser(User user);
}
