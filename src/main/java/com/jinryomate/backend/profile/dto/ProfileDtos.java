package com.jinryomate.backend.profile.dto;

import com.jinryomate.backend.profile.entity.FieldSource;
import com.jinryomate.backend.profile.entity.FieldStatus;
import com.jinryomate.backend.profile.entity.HealthProfile;
import com.jinryomate.backend.profile.entity.Sex;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 온보딩 프로필의 요청·응답 모음. */
public final class ProfileDtos {

    private ProfileDtos() {}

    // ---------- 요청 ----------

    public record ListFieldRequest(
            @NotNull(message = "상태가 필요합니다.")
            FieldStatus status,

            @Size(max = 20, message = "항목은 최대 20개까지입니다.")
            List<@NotBlank @Size(max = 50) String> items
    ) {}

    public record TextFieldRequest(
            @NotNull(message = "상태가 필요합니다.")
            FieldStatus status,

            @Size(max = 200, message = "200자 이내로 입력해주세요.")
            String text
    ) {}

    /**
     * 온보딩 완료 저장.
     *
     * <p>나이가 아니라 <b>출생연도</b>를 받는다. 나이를 저장하면 해가 바뀔 때 낡고,
     * 카카오에서 받는 값도 출생연도다.
     */
    public record HealthProfileRequest(
            @NotBlank(message = "이름이 필요합니다.")
            @Size(max = 30, message = "이름은 30자 이내입니다.")
            String name,

            @NotNull(message = "출생연도가 필요합니다.")
            @Min(value = 1900, message = "출생연도를 확인해주세요.")
            @Max(value = 2100, message = "출생연도를 확인해주세요.")
            Integer birthYear,

            /** {@code "MM-dd"}. 선택. 있으면 만 나이를 정확히 계산한다. */
            @Pattern(regexp = "^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$",
                    message = "생일은 MM-dd 형식입니다.")
            String birthMonthDay,

            @NotNull(message = "성별이 필요합니다.")
            Sex sex,

            @NotNull @Valid ListFieldRequest medications,
            @NotNull @Valid ListFieldRequest conditions,
            @NotNull @Valid TextFieldRequest allergies
    ) {}

    // ---------- 응답 ----------

    public record ListFieldResponse(FieldStatus status, List<String> items) {}

    public record TextFieldResponse(FieldStatus status, String text) {}

    /**
     * @param sources             각 필드가 카카오에서 왔는지 사용자가 넣은 것인지.
     *                            앱이 "카카오에서 가져왔어요" 같은 안내를 띄울 수 있게 준다
     * @param onboardingCompleted 온보딩을 실제로 마쳤는지. 카카오 값이 채워진 것만으로는 완료가 아니다
     * @param canStartIntake      문답을 시작할 수 있는지. 나이·성별이 있어야 한다
     */
    public record HealthProfileResponse(
            String name,
            Integer birthYear,
            String birthMonthDay,
            Integer age,
            Sex sex,
            ListFieldResponse medications,
            ListFieldResponse conditions,
            TextFieldResponse allergies,
            Sources sources,
            boolean onboardingCompleted,
            boolean canStartIntake
    ) {
        public record Sources(FieldSource name, FieldSource birthYear, FieldSource sex) {}

        public static HealthProfileResponse from(HealthProfile p) {
            return new HealthProfileResponse(
                    p.getName(),
                    p.getBirthYear(),
                    p.getBirthMonthDay(),
                    p.age(),
                    p.getSex(),
                    new ListFieldResponse(p.getMedicationsStatus(), List.copyOf(p.getMedications())),
                    new ListFieldResponse(p.getConditionsStatus(), List.copyOf(p.getConditions())),
                    new TextFieldResponse(p.getAllergiesStatus(), p.getAllergies()),
                    new Sources(p.getNameSource(), p.getBirthYearSource(), p.getSexSource()),
                    p.isOnboardingCompleted(),
                    p.canStartIntake());
        }

        /** 프로필이 아직 없는 사용자. 앱이 빈 온보딩 화면을 띄우면 된다. */
        public static HealthProfileResponse empty() {
            return new HealthProfileResponse(
                    null, null, null, null, null,
                    new ListFieldResponse(FieldStatus.UNKNOWN, List.of()),
                    new ListFieldResponse(FieldStatus.UNKNOWN, List.of()),
                    new TextFieldResponse(FieldStatus.UNKNOWN, null),
                    new Sources(null, null, null),
                    false,
                    false);
        }
    }
}
