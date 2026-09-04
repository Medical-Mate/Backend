package com.jinryomate.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.attachment.repository.AttachmentRepository;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.auth.repository.UserRepository;
import com.jinryomate.backend.card.repository.BriefingCardRepository;
import com.jinryomate.backend.handoff.repository.ShareLinkRepository;
import com.jinryomate.backend.intake.dto.IntakeDtos.StartSessionRequest;
import com.jinryomate.backend.intake.repository.IntakeSessionRepository;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
import com.jinryomate.backend.profile.repository.HealthProfileRepository;
import com.jinryomate.backend.visit.dto.VisitDtos.CreateVisitRequest;
import com.jinryomate.backend.visit.repository.VisitRecordRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터를 다 채운 상태에서 탈퇴한다.
 *
 * <p>{@code AuthApiTest} 의 탈퇴 테스트는 로그인 직후에 지워서 <b>삭제 순서를 건드리지 않는다</b>.
 * 첨부는 기록·프로필을, 공유 링크는 카드를 참조하므로 순서가 틀리면 외래키에 걸리는데,
 * 빈 사용자로는 그게 드러나지 않는다.
 *
 * <p>도메인이 늘어 {@code withdraw()} 에 줄이 붙을 때마다 여기가 먼저 깨져야 한다.
 */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class WithdrawCascadeTest {

    private static final long KAKAO_ID = 5566778899L;
    private static final byte[] IMAGE_BYTES = "가짜-이미지-바이트".getBytes();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Autowired UserRepository userRepository;
    @Autowired HealthProfileRepository profileRepository;
    @Autowired IntakeSessionRepository sessionRepository;
    @Autowired BriefingCardRepository cardRepository;
    @Autowired VisitRecordRepository visitRepository;
    @Autowired AttachmentRepository attachmentRepository;
    @Autowired ShareLinkRepository shareLinkRepository;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("프로필·카드·기록·첨부·공유 링크가 다 있어도 탈퇴가 끝까지 지운다")
    void 전체_삭제() throws Exception {
        completeOnboarding();
        uploadProfileAttachment();

        long cardId = confirmedCard();
        issueShareLink(cardId);

        long visitId = createVisit(cardId);
        uploadVisitAttachment(visitId);

        // 여기까지가 환자 한 명이 S1~S6 를 다 거친 상태다.
        assertThat(profileRepository.count()).isOne();
        assertThat(cardRepository.count()).isOne();
        assertThat(visitRepository.count()).isOne();
        assertThat(attachmentRepository.count()).isEqualTo(2);
        assertThat(shareLinkRepository.count()).isOne();

        mockMvc.perform(delete("/api/me").header("Authorization", token))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByKakaoId(KAKAO_ID)).isEmpty();
        assertThat(profileRepository.count()).isZero();
        assertThat(sessionRepository.count()).isZero();
        assertThat(cardRepository.count()).isZero();
        assertThat(visitRepository.count()).isZero();
        assertThat(attachmentRepository.count()).isZero();
        assertThat(shareLinkRepository.count()).isZero();
    }

    // ---------- helpers ----------

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("file", filename, "image/jpeg", IMAGE_BYTES);
    }

    private void uploadProfileAttachment() throws Exception {
        mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("bag.jpg"))
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    private void uploadVisitAttachment(long visitId) throws Exception {
        mockMvc.perform(multipart("/api/visits/" + visitId + "/attachments")
                        .file(image("prescription.jpg"))
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    private void issueShareLink(long cardId) throws Exception {
        mockMvc.perform(post("/api/cards/" + cardId + "/share").header("Authorization", token))
                .andExpect(status().isOk());
    }

    private long createVisit(long cardId) throws Exception {
        String body = mockMvc.perform(post("/api/cards/" + cardId + "/visit")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateVisitRequest(
                                "○○정형외과", LocalDate.now(), "혈액검사", "3일 뒤 확인",
                                "나프록센 500mg·하루 2번 식후", "피검사 해보자고 하셨어요"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("visitId").asLong();
    }

    private long confirmedCard() throws Exception {
        String session = mockMvc.perform(post("/api/sessions")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new StartSessionRequest(List.of("hand_finger_joint_R"), "손가락 관절(오른손)"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long sessionId = objectMapper.readTree(session).path("sessionId").asLong();

        String card = mockMvc.perform(post("/api/sessions/" + sessionId + "/card")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long cardId = objectMapper.readTree(card).path("cardId").asLong();

        mockMvc.perform(post("/api/cards/" + cardId + "/confirm").header("Authorization", token))
                .andExpect(status().isOk());
        return cardId;
    }

    private void completeOnboarding() throws Exception {
        mockMvc.perform(put("/api/me/health-profile")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new HealthProfileRequest(
                                "김서연", 1994, "03-03", Sex.FEMALE,
                                new ListFieldRequest(FieldStatus.KNOWN, List.of("이부프로펜")),
                                new ListFieldRequest(FieldStatus.NONE, List.of()),
                                new TextFieldRequest(FieldStatus.UNKNOWN, null)))))
                .andExpect(status().isOk());
    }

    private TokenResponse login() throws Exception {
        String body = mockMvc.perform(post("/api/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new KakaoLoginRequest("kakao-token"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readValue(body, TokenResponse.class);
    }
}
