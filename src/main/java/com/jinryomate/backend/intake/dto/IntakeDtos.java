package com.jinryomate.backend.intake.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;
import com.jinryomate.backend.intake.entity.AiProfile;
import com.jinryomate.backend.intake.entity.IntakeMessage;
import com.jinryomate.backend.intake.entity.IntakeSession;
import com.jinryomate.backend.intake.entity.Side;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 문답 세션의 요청·응답. */
public final class IntakeDtos {

    private IntakeDtos() {}

    /**
     * 세션 시작. 화면 S1.5에서 짚은 부위를 함께 보낸다.
     *
     * <p>부위를 건너뛰어도 시작할 수 있다. 와이어프레임에 "부위 짚기"를 건너뛰는 경로가 있다.
     */
    public record StartSessionRequest(
            /**
             * 부위 마스터의 노드 id. {@code ANC:001} · {@code SUR:032}.
             *
             * <p><b>하나만 보낸다.</b> AI 가 세션 시작에 부위 하나를 받는다. 없는 id 는 400이다.
             *
             * <p><b>{@code site_node_id} 로 보내도 받는다.</b> 같은 값을 AI 계약은
             * {@code site_node_id} 로, 우리는 {@code siteNodeId} 로 부른다. 앱이 AI 문서를
             * 보고 뱀 표기로 보내면 Jackson 이 <b>조용히 버리고</b> 부위 없는 세션이 200 으로
             * 열린다 — 실제로 그렇게 났다. 400 도 안 나고 화면도 멀쩡해 보인다.
             *
             * <p>별칭을 들이는 것이 계약을 흐리는 일인 건 맞다. 다만 여기서는 <b>틀린 이름이
             * 조용히 통과하는 것</b>이 더 나쁘다 — 부위는 문답 전체의 출발점이라 없으면
             * 처음부터 다시 묻는다.
             */
            @JsonAlias("site_node_id")
            @Size(max = 40, message = "부위 코드가 너무 깁니다.")
            String siteNodeId,

            /**
             * 추출을 어디서 돌리는지. 비우면 {@code SERVER} 다.
             *
             * <p><b>앱이 정한다.</b> 폰에 모델을 들고 있는지는 기기 사정이라 서버가 알 수 없다.
             * {@code ONDEVICE} 면 문답도 달라진다 — 첫 자유 발화 없이 첫 축 질문부터 시작한다.
             */
            AiProfile profile,

            /** 좌우. 좌우가 없는 부위(머리·배 등 13곳)에 보내면 400이다. */
            Side side,

            @Size(max = 100, message = "부위 표현은 100자 이내입니다.")
            String siteText
    ) {}

    /**
     * 환자 발화 한 줄.
     *
     * <p>음성으로 말했어도 <b>오디오를 받지 않는다.</b> 앱이 변환한 텍스트만 온다 —
     * "녹음은 저장하지 않는다"는 원칙 때문이다. {@code inputMethod} 는 그게
     * 타이핑이었는지 음성이었는지만 남긴다.
     *
     * <p>2000자 상한은 {@code intake_messages.text} 컬럼 길이다. AI 는 300자를 넘으면
     * 잘라서 반영하고 안내 문구를 붙이는데, 그건 AI 쪽 동작이라 여기서 막지 않는다.
     */
    public record SendMessageRequest(
            @NotBlank(message = "내용을 입력해주세요.")
            @Size(max = 2000, message = "한 번에 2000자까지 보낼 수 있습니다.")
            String text,

            /** 비우면 {@code TEXT} 로 본다. */
            IntakeMessage.InputMethod inputMethod,

            /**
             * 폰 안에서 돌린 추출 결과. 온디바이스 프로필에서만 보낸다.
             *
             * <p><b>우리는 열어보지 않고 AI 에 그대로 넘긴다.</b> 형식은 AI 계약이 정하고,
             * 우리가 구조를 읽기 시작하면 AI 쪽 변경이 우리를 깨뜨린다.
             *
             * <p><b>저장하지 않는다.</b> 증상 텍스트가 든 중간 산출물이고, 카드에 근거가
             * 이미 남으므로 우리가 또 들고 있을 이유가 없다.
             */
            JsonNode extraction,

            /** 어떤 폰 모델·프롬프트가 만들었는지. 카드의 {@code provenance} 가 이 값으로 갱신된다. */
            JsonNode extractionMeta
    ) {}

    /**
     * 통증 강도. 화면 3단계({@code 1d}) 슬라이더.
     *
     * <p><b>1~5 서열척도다. NRS 0~10 이 아니다.</b> 와이어프레임 슬라이더가 다섯 칸이다.
     *
     * <p>{@code label} 은 앱이 보낸다 — "꽤 아파요" 같은 문구는 디자인 카피라 서버가
     * 들고 있으면 바꿀 때마다 배포해야 한다.
     */
    public record SeverityRequest(
            @NotNull(message = "통증 정도를 골라주세요.")
            @Min(value = 1, message = "통증 정도는 1~5입니다.")
            @Max(value = 5, message = "통증 정도는 1~5입니다.")
            Integer level,

            @Size(max = 40, message = "표시 문구는 40자 이내입니다.")
            String label
    ) {}

    /**
     * 의사에게 물어볼 것. 화면 4단계({@code 1i}).
     *
     * <p><b>목록을 통째로 보낸다.</b> 추가·편집·삭제·순서변경이 전부 이 한 번으로 처리된다.
     * 빈 배열을 보내면 전부 지운다.
     */
    public record QuestionsRequest(
            @NotNull(message = "질문 목록이 필요합니다. 비우려면 빈 배열을 보내주세요.")
            @Size(max = MAX_QUESTIONS, message = "질문은 최대 " + MAX_QUESTIONS + "개까지입니다.")
            List<@NotBlank(message = "빈 질문은 담을 수 없습니다.")
                 @Size(max = MAX_QUESTION_LENGTH,
                       message = "질문은 " + MAX_QUESTION_LENGTH + "자 이내입니다.") String> questions
    ) {}

    /**
     * 화면 {@code 1i} 의 "적어둔 질문". <b>이 상한이 유일한 원천이다</b> — 카드 편집
     * ({@code CardDtos})과 카드 생성 검증({@code CardContentValidator})이 이 값을 본다.
     *
     * <p><b>셋이 따로 3 을 들고 있다가 겪었다.</b> 저장은 통과하는데 카드를 만들 때 앞에서
     * 잘려서, 앱에서는 저장이 성공했는데 카드에는 세 개만 있었다(#132).
     *
     * <p><b>3 에서 5 로 올렸다.</b> AI 후보가 3 개라 그걸 다 담으면 환자가 자기 질문을
     * 하나도 못 적었다. 두 개를 더 적을 자리를 둔다.
     */
    public static final int MAX_QUESTIONS = 5;

    /**
     * 질문 한 줄의 길이.
     *
     * <p><b>40 자가 아니다.</b> 40 은 AI 가 만든 문구를 낭독 모드 큰 글자로 보이려는
     * 기준인데, 카드의 질문은 <b>환자가 직접 쓴 글</b>이라 그 기준이 걸릴 자리가 아니다.
     * AI 가 만든 후보는 {@code questionCandidates} 로 따로 오고 여기 섞이지 않는다.
     *
     * <p>실제로 41 자 질문이 저장은 200 OK 로 되고 카드를 만들 때 <b>통째로 사라졌다.</b>
     * 화면에서 길어지는 것은 줄바꿈으로 풀 일이지, 환자가 쓴 글을 말없이 버릴 일이 아니다.
     */
    public static final int MAX_QUESTION_LENGTH = 200;

    /**
     * AI 가 만든 질문 후보. 화면 {@code 1i} 에서 환자가 골라 담는다.
     *
     * <p><b>최종 목록이 아니다.</b> 고른 것과 직접 쓴 것을 합친 결과는
     * {@code PUT /api/sessions/{id}/questions} 로 따로 보낸다.
     *
     * <p>문답이 끝나기 전에는 빈 배열이다.
     *
     * @param rank 낮을수록 먼저. 이미 이 순서로 정렬해서 내려준다
     */
    public record QuestionCandidate(
            String text,
            String source,
            Integer rank,

            /**
             * 어디서 왔나. {@code model}(폰 후보가 AI 검증을 통과) · {@code template}(카드 값으로
             * 만든 보충). <b>폰 후보를 보낸 세션에서만 온다.</b> 없으면 키가 빠진다.
             */
            String origin
    ) {}

    /**
     * 폰이 만든 질문 후보. 화면 {@code 1i} 전에 보낸다(#164).
     *
     * <p><b>AI 가 검증하고 모자라면 채운다.</b> 카드에 없는 말·병명·반복은 버리고, 남은 것이
     * 모자라면 카드 값으로 만든 템플릿을 뒤에 붙인다. 외부 LLM 은 부르지 않는다.
     *
     * <p><b>폰이 하나도 못 만들었으면 빈 배열을 보내세요.</b> 템플릿으로만 채웁니다.
     *
     * <p>문답이 끝난 뒤에만 보낼 수 있다. 다시 보내면 후보를 갈아끼운다.
     */
    public record PhoneQuestionCandidatesRequest(
            @NotNull(message = "후보 목록이 필요합니다. 없으면 빈 배열을 보내주세요.")
            @Size(max = 10, message = "후보는 10개까지 보낼 수 있습니다.")
            List<@NotNull @Valid PhoneQuestionCandidate> candidates,

            /**
             * 어떤 폰 모델·프롬프트가 만들었나({@code model_id} · {@code prompt_version}).
             * <b>열어보지 않고 AI 에 그대로 넘긴다.</b>
             */
            JsonNode candidatesMeta
    ) {}

    /**
     * @param source 어느 칸에서 나온 질문인가(폰 모델이 단 것). 틀려도 AI 가 다시 본다
     */
    public record PhoneQuestionCandidate(
            @NotBlank(message = "빈 후보는 보낼 수 없습니다.")
            @Size(max = 200, message = "후보는 200자 이내입니다.")
            String text,

            @Size(max = 40, message = "source 는 40자 이내입니다.")
            String source
    ) {}

    /**
     * 카드의 축 하나. 폰이 질문 후보를 만드는 재료다(#164).
     *
     * <p>AI 가 준 순서·값 그대로다. {@code status} 는 카드 축 5값
     * ({@code not_asked} · {@code filled} · {@code unknown} · {@code skipped} · {@code ambiguous}).
     *
     * @param key   축 키. {@code site} · {@code onset} …
     * @param value 값. {@code filled} 가 아니면 대개 없다(키가 빠진다)
     */
    public record CardAxis(String key, String status, String value) {}

    /**
     * 마지막 AI 문장이 묻는 칸. <b>폰 추출 프롬프트의 첫 줄 재료다</b>(#157).
     *
     * <p>{@code 질문한 항목: {label} (axis={key}). 이 항목만 답한다.} 에 <b>그대로</b>
     * 넣는다. 값은 AI 가 폰 프롬프트와 같은 원천에서 내고 우리는 가공하지 않는다 —
     * 앱이 따로 표를 들 필요가 없다.
     *
     * <p>응답의 {@code askingAxis} 가 {@code null} 이면 문답이 끝났거나(폰 추출 붙이지
     * 않음) 서버 프로필의 첫 자유 발화다.
     *
     * @param key   축 키. {@code onset} · {@code character} …
     * @param label 프롬프트에 넣을 말. {@code 언제부터·어떻게 시작}
     */
    public record AskingAxis(String key, String label) {
        static AskingAxis of(IntakeSession s) {
            return s.getAskingAxisKey() == null ? null : new AskingAxis(s.getAskingAxisKey(), s.getAskingAxisLabel());
        }
    }

    public record MessageResponse(int seq, IntakeMessage.Role role, String text) {
        public static MessageResponse from(IntakeMessage m) {
            return new MessageResponse(m.getSeq(), m.getRole(), m.getText());
        }
    }

    /**
     * 턴 처리 결과.
     *
     * <p>{@code messages} 에 대화 전체를 다시 실어 보낸다. 앱이 화면을 다시 그릴 때
     * 세션을 또 조회하지 않아도 되고, 중간에 유실된 줄이 있어도 여기서 맞춰진다.
     */
    public record TurnResponse(
            Long sessionId,
            IntakeSession.Status status,
            /** 환자에게 보여줄 다음 문장. 질문이거나 마무리 인사다. */
            String reply,
            boolean ended,
            String endReason,
            SessionResponse.Progress progress,
            List<MessageResponse> messages,

            /** {@code reply} 가 묻는 칸. 폰 추출 프롬프트에 쓴다. 끝났으면 {@code null}. */
            AskingAxis askingAxis
    ) {
        public static TurnResponse of(IntakeSession s, String reply, boolean ended) {
            return new TurnResponse(
                    s.getId(),
                    s.getStatus(),
                    reply,
                    ended,
                    s.getEndReason(),
                    new SessionResponse.Progress(s.getProgressCurrent(), s.getProgressTotal()),
                    s.getMessages().stream().map(MessageResponse::from).toList(),
                    AskingAxis.of(s));
        }
    }

    /**
     * 세션 전체.
     *
     * <p>3·4단계 값도 함께 담는다. 사용자가 뒤로 갔을 때 <b>고른 값이 그대로 복원</b>돼야
     * 하는데, 세션 조회 하나로 화면 넷을 다 그릴 수 있어야 그게 된다.
     */
    public record SessionResponse(
            Long sessionId,
            IntakeSession.Status status,
            String siteNodeId,
            Side side,
            String siteText,
            Progress progress,
            List<MessageResponse> messages,
            /** 3단계. 아직 안 골랐으면 {@code null}. */
            Severity severity,
            /** 4단계. 아직 안 적었으면 빈 배열. */
            List<String> questions,

            /**
             * AI 가 만든 질문 후보. <b>문답이 끝나야 채워진다.</b>
             *
             * <p>카드가 아니라 여기 실리는 이유 — 화면 순서가 문답 → 강도 → 물어볼 것 →
             * 카드라, 후보가 필요한 시점에는 카드가 아직 없다.
             */
            List<QuestionCandidate> questionCandidates,

            /**
             * 카드의 축 값·상태. <b>폰이 질문 후보를 만드는 재료다.</b> 문답 중에도 그때까지의
             * 값이 나간다. 강도(3단계)는 여기가 아니라 {@code severity} 에 있다.
             */
            List<CardAxis> cardAxes,

            /**
             * 마지막 AI 문장이 묻는 칸. <b>세션에 저장해 두므로 재조회에도 같은 값이 온다</b> —
             * 앱을 다시 켜도 폰 추출을 이어 갈 수 있다. 끝났으면 {@code null}.
             */
            AskingAxis askingAxis
    ) {
        public record Progress(int current, int total) {}

        public record Severity(int level, String label) {}

        public static SessionResponse from(IntakeSession s) {
            return from(s, List.of(), List.of());
        }

        public static SessionResponse from(IntakeSession s, List<QuestionCandidate> candidates,
                                           List<CardAxis> cardAxes) {
            return new SessionResponse(
                    s.getId(),
                    s.getStatus(),
                    s.getSiteNodeId(),
                    s.getSide(),
                    s.getSiteText(),
                    new Progress(s.getProgressCurrent(), s.getProgressTotal()),
                    s.getMessages().stream().map(MessageResponse::from).toList(),
                    s.getSeverityLevel() == null
                            ? null
                            : new Severity(s.getSeverityLevel(), s.getSeverityLabel()),
                    List.copyOf(s.getQuestions()),
                    List.copyOf(candidates),
                    List.copyOf(cardAxes),
                    AskingAxis.of(s));
        }
    }
}
