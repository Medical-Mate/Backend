package com.jinryomate.backend.demo.service;

import static java.util.Map.entry;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * 웹 데모가 보낼 수 있는 이벤트와 그 속성의 전부.
 *
 * <p><b>이 클래스가 원문 누출을 막는 유일한 지점입니다.</b> 클라이언트가 보낸 것을 그대로
 * 저장하면 언젠가 증상 텍스트가 섞입니다 — 프론트 버그로든, 누가 손으로 찔러 보든.
 * 그래서 이벤트 이름도, 속성 키도, 값의 모양도 <b>서버가 정합니다.</b>
 *
 * <p><b>목록 밖은 400 입니다.</b> 조용히 버리지 않습니다. 버리면 프론트가 스펙과 어긋난
 * 것을 모르고, 우리는 "이벤트가 안 쌓이는데 왜인지 모르는" 상태가 됩니다 — 이 저장소가
 * 제일 경계하는 모양입니다.
 *
 * <p><b>없는 속성은 괜찮습니다.</b> 지연을 못 재는 브라우저가 있을 수 있어 <b>있을 때만</b>
 * 검사합니다. 강제하면 잴 수 없는 환경에서 이벤트가 통째로 막힙니다.
 *
 * <p>목록을 늘릴 때는 "이 값으로 어떤 질문에 답하나"를 먼저 적으세요. 답이 없으면
 * 저장소와 개인정보 처리 범위만 커집니다.
 */
public final class DemoEventCatalog {

    private DemoEventCatalog() {
    }

    /** 카드 8축. 운영 {@code /api/demo/body-map} 의 {@code axes} 와 같은 값입니다. */
    private static final String[] AXES = {
            "site", "onset", "character", "radiation",
            "associated", "time_course", "exacerbating_relieving", "severity"
    };

    /** 진료 후 기록 라벨. AI 가 메모를 이 축으로 나눕니다. */
    private static final String[] MEMO_LABELS = {
            "findings", "tests", "medication_instructions",
            "lifestyle_instructions", "follow_up", "other"
    };

    /** 데모 경로. {@code guard.*} 가 어느 길에서 막혔는지 가리킵니다. */
    private static final String[] DEMO_PATHS = {
            "body-map", "previsit/sessions", "previsit/turns",
            "postvisit/memo", "hospitals", "events"
    };

    /** AI 를 부르는 단계. 예산 소진·장애가 어디서 났는지 가리킵니다. */
    private static final String[] AI_STEPS = {"session", "turn", "questions", "memo"};

    private static final String[] NET_ERRORS = {"422", "502", "503", "timeout", "network"};

    /**
     * 이벤트 이름 → (속성 키 → 값 규칙).
     *
     * <p>속성이 없는 이벤트는 빈 맵입니다. 그 자체로 "일어났다"가 정보입니다.
     */
    private static final Map<String, Map<String, Predicate<Object>>> EVENTS = Map.ofEntries(

            /* ── 진입과 소개 ─────────────────────────────────────────── */
            entry("app.opened", Map.of("entry", oneOf("direct", "shared_link"))),
            entry("intro.viewed", Map.of("page", intRange(1, 8))),
            entry("intro.skipped", Map.of("page", intRange(1, 8))),
            entry("intro.done", Map.of()),

            /* ── 부위 짚기 ──────────────────────────────────────────── */
            entry("site.map_viewed", Map.of("view", matches("front|back|ANC:\\d{3}"))),
            entry("site.node_selected", Map.of(
                    "node_id", matches("(ANC|SUR):\\d{3}"),
                    "side", oneOf("left", "right", "center"))),
            entry("site.search_used", Map.of("result_count", intRange(0, 999))),
            entry("site.skipped", Map.of()),

            /* ── 증상 문답 ──────────────────────────────────────────── */
            entry("intake.started", Map.of("has_site", bool())),
            entry("intake.turn_sent", Map.of(
                    "turn_no", intRange(1, 50),
                    "input_mode", oneOf("text", "voice", "choice"),
                    "char_count", intRange(0, 5000))),
            entry("intake.turn_received", Map.of(
                    "turn_no", intRange(1, 50),
                    "latency_ms", intRange(0, 300_000),
                    "axes_filled", intRange(0, 8),
                    "axes_ambiguous", intRange(0, 8))),
            entry("intake.turn_failed", Map.of(
                    "turn_no", intRange(1, 50),
                    "error_code", oneOf(NET_ERRORS),
                    "latency_ms", intRange(0, 300_000))),
            entry("intake.turn_retried", Map.of("turn_no", intRange(1, 50))),
            entry("intake.abandoned", Map.of(
                    "turn_no", intRange(0, 50),
                    "elapsed_sec", intRange(0, 86_400))),
            entry("intake.ended", Map.of(
                    "turn_count", intRange(0, 50),
                    "ended_by", oneOf("ai", "user_stop"),
                    "elapsed_sec", intRange(0, 86_400))),

            /* ── 통증 강도 ──────────────────────────────────────────── */
            entry("severity.viewed", Map.of()),
            entry("severity.selected", Map.of("value", intRange(0, 10))),
            entry("severity.skipped", Map.of()),

            /* ── 물어볼 것 ──────────────────────────────────────────── */
            entry("questions.requested", Map.of()),
            entry("questions.received", Map.of(
                    "count", intRange(0, 10),
                    "latency_ms", intRange(0, 300_000))),
            entry("questions.edited", Map.of(
                    "action", oneOf("add", "remove", "edit"),
                    "final_count", intRange(0, 10))),
            entry("questions.skipped", Map.of()),

            /* ── 브리핑 카드 ────────────────────────────────────────── */
            entry("card.viewed", Map.of(
                    "axes_filled", intRange(0, 8),
                    "axes_not_asked", intRange(0, 8),
                    "axes_ambiguous", intRange(0, 8),
                    "has_department", bool())),
            entry("card.axis_edited", Map.of("axis_key", oneOf(AXES))),
            entry("card.confirmed", Map.of()),
            entry("card.deleted", Map.of()),

            /* ── 진료 후 기록 ───────────────────────────────────────── */
            entry("memo.started", Map.of("input_mode", oneOf("text", "voice"))),
            entry("memo.submitted", Map.of("char_count", intRange(0, 5000))),
            entry("memo.classified", Map.of(
                    "latency_ms", intRange(0, 300_000),
                    "label_count", intRange(0, 20),
                    "split_version", matches("[A-Za-z0-9._-]{1,16}"))),
            entry("memo.failed", Map.of("error_code", oneOf(NET_ERRORS))),
            entry("memo.label_corrected", Map.of(
                    "from_label", oneOf(MEMO_LABELS),
                    "to_label", oneOf(MEMO_LABELS))),
            entry("memo.abandoned", Map.of()),

            /* ── 병원 검색 ──────────────────────────────────────────── */
            entry("hospital.searched", Map.of(
                    "result_count", intRange(0, 999),
                    "latency_ms", intRange(0, 300_000))),
            entry("hospital.selected", Map.of("rank", intRange(0, 999))),
            entry("hospital.zero_results", Map.of()),

            /* ── 막힘 ──────────────────────────────────────────────── */
            entry("guard.rate_limited", Map.of("path", oneOf(DEMO_PATHS))),
            entry("guard.payload_too_large", Map.of("path", oneOf(DEMO_PATHS))),
            entry("budget.exhausted", Map.of("step", oneOf(AI_STEPS))),
            entry("upstream.failed", Map.of(
                    "step", oneOf(AI_STEPS),
                    "error_code", oneOf(NET_ERRORS))),

            /* ── 서버만 아는 것 ────────────────────────────────────────
             * 브라우저는 자기가 받은 것만 안다. AI 가 실제로 얼마나 걸렸는지는
             * 프록시에서만 보인다 — 느린 게 우리인지 Bedrock 인지 여기서 갈린다.
             */
            entry("ai.responded", Map.of(
                    "step", oneOf(AI_STEPS),
                    "latency_ms", intRange(0, 300_000),
                    "status", intRange(100, 599)))
    );

    /** 아는 이벤트인가. */
    public static boolean knows(String event) {
        return EVENTS.containsKey(event);
    }

    /**
     * 아는 데모 경로인가.
     *
     * <p>{@code guard.*} 를 남기는 쪽이 요청 URI 를 그대로 넣으면 목록 밖 값이라
     * 기록이 통째로 떨어진다. 넣기 전에 물어볼 수 있게 연다.
     */
    public static boolean knowsDemoPath(String path) {
        return Set.of(DEMO_PATHS).contains(path);
    }

    public static Set<String> names() {
        return EVENTS.keySet();
    }

    /**
     * 이벤트 하나를 검사한다.
     *
     * @return 문제가 있으면 <b>사람이 읽을 수 있는</b> 이유. 통과면 비어 있다
     */
    public static Optional<String> check(String event, Map<String, Object> props) {
        Map<String, Predicate<Object>> spec = EVENTS.get(event);
        if (spec == null) {
            return Optional.of("알 수 없는 이벤트입니다: " + event);
        }
        if (props == null || props.isEmpty()) {
            return Optional.empty();
        }
        for (Map.Entry<String, Object> p : props.entrySet()) {
            Predicate<Object> rule = spec.get(p.getKey());
            if (rule == null) {
                // 값은 절대 메시지에 싣지 않는다. 그 자체가 새면 안 되는 것일 수 있다.
                return Optional.of("허용하지 않는 속성입니다: " + event + "." + p.getKey());
            }
            if (!rule.test(p.getValue())) {
                return Optional.of("속성 값이 규격 밖입니다: " + event + "." + p.getKey());
            }
        }
        return Optional.empty();
    }

    /* ── 규칙 ─────────────────────────────────────────────────────── */

    /** 정수. 소수점이 붙어 오면 거부한다 — JSON 숫자는 실수로 올 수 있다. */
    private static Predicate<Object> intRange(int min, int max) {
        return v -> v instanceof Number n
                && n.doubleValue() == Math.rint(n.doubleValue())
                && n.doubleValue() >= min
                && n.doubleValue() <= max;
    }

    private static Predicate<Object> bool() {
        return v -> v instanceof Boolean;
    }

    private static Predicate<Object> oneOf(String... values) {
        Set<String> allowed = Set.of(values);
        return v -> v instanceof String s && allowed.contains(s);
    }

    private static Predicate<Object> matches(String regex) {
        Pattern pattern = Pattern.compile(regex);
        return v -> v instanceof String s && pattern.matcher(s).matches();
    }
}
