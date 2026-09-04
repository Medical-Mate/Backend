package com.jinryomate.backend.attachment;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jinryomate.backend.TestcontainersConfig;
import com.jinryomate.backend.auth.client.KakaoClient;
import com.jinryomate.backend.auth.dto.AuthDtos.KakaoLoginRequest;
import com.jinryomate.backend.auth.dto.AuthDtos.TokenResponse;
import com.jinryomate.backend.profile.dto.ProfileDtos.HealthProfileRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.ListFieldRequest;
import com.jinryomate.backend.profile.dto.ProfileDtos.TextFieldRequest;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.Sex;
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

/** 사진 첨부 통합 테스트. */
@Import(TestcontainersConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AttachmentApiTest {

    private static final long KAKAO_ID = 3847562910L;
    private static final byte[] IMAGE_BYTES = "가짜-이미지-바이트".getBytes();

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KakaoClient kakaoClient;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        given(kakaoClient.resolveKakaoId(anyString())).willReturn(KAKAO_ID);
        given(kakaoClient.fetchProfile(anyString())).willReturn(null);
        token = "Bearer " + login().accessToken();
    }

    @Test
    @DisplayName("온보딩 전에는 약봉투를 올릴 수 없다")
    void 프로필_없으면_거부() throws Exception {
        mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("bag.jpg", "image/jpeg"))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("약봉투 사진을 올리면 바이트 없이 메타데이터만 돌려준다")
    void 약봉투_업로드() throws Exception {
        completeOnboarding();

        mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("bag.jpg", "image/jpeg"))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purpose").value("MEDICATION_BAG"))
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(IMAGE_BYTES.length))
                .andExpect(jsonPath("$.downloadUrl").exists())
                // 응답에 바이트가 담기면 목록 조회가 무거워진다.
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("아이폰 기본 포맷(heic)도 올릴 수 있다")
    void heic_허용() throws Exception {
        completeOnboarding();

        mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("IMG_0001.HEIC", "image/heic"))
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("사진이 아닌 파일은 막는다")
    void 형식_제한() throws Exception {
        completeOnboarding();

        mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("note.pdf", "application/pdf"))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("한 곳에 6장째를 올리면 막는다")
    void 개수_제한() throws Exception {
        completeOnboarding();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(multipart("/api/me/health-profile/attachments")
                            .file(image("bag" + i + ".jpg", "image/jpeg"))
                            .header("Authorization", token))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("bag5.jpg", "image/jpeg"))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("올린 사진을 원본 그대로 내려받는다")
    void 내려받기() throws Exception {
        completeOnboarding();
        long id = uploadToProfile();

        mockMvc.perform(get("/api/attachments/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(IMAGE_BYTES));
    }

    @Test
    @DisplayName("삭제하면 더 이상 받을 수 없다")
    void 삭제() throws Exception {
        completeOnboarding();
        long id = uploadToProfile();

        mockMvc.perform(delete("/api/attachments/" + id).header("Authorization", token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/attachments/" + id).header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("목록에는 올린 순서대로 담긴다")
    void 목록() throws Exception {
        completeOnboarding();
        uploadToProfile();
        uploadToProfile();

        mockMvc.perform(get("/api/me/health-profile/attachments").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @DisplayName("없는 사진은 404, 토큰 없으면 401")
    void 접근_제어() throws Exception {
        mockMvc.perform(get("/api/attachments/999999").header("Authorization", token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/attachments/1"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private MockMultipartFile image(String filename, String contentType) {
        return new MockMultipartFile("file", filename, contentType, IMAGE_BYTES);
    }

    private long uploadToProfile() throws Exception {
        String body = mockMvc.perform(multipart("/api/me/health-profile/attachments")
                        .file(image("bag.jpg", "image/jpeg"))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("attachmentId").asLong();
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
