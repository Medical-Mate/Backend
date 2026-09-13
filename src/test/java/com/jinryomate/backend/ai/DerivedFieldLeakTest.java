package com.jinryomate.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 응답에 실리는 레코드의 <b>파생 메서드가 필드로 새지 않는지</b> 본다.
 *
 * <p>Jackson 은 레코드의 {@code isXxx()} · {@code getXxx()} 를 게터로 보고 응답에 싣는다.
 * {@code isEmpty()} 하나 붙였다가 {@code "empty": false} 가 앱에 나갔고, 그걸 고쳐 놓고
 * 다음 레코드에서 똑같이 반복했다.
 *
 * <p>한 건씩 막지 않고 <b>규칙으로</b> 막는다 — 새 레코드를 만들 때 자동으로 걸린다.
 */
class DerivedFieldLeakTest {

    /** 응답 본문에 실리는 {@code ai.dto} 레코드들. */
    private static final List<Class<?>> RESPONSE_RECORDS = List.of(
            com.jinryomate.backend.ai.dto.FollowUp.class,
            com.jinryomate.backend.ai.dto.LabelsMeta.class);

    @Test
    @DisplayName("레코드 성분이 아닌 메서드는 JSON 에 나가지 않는다")
    void 파생_메서드는_응답에_안_실린다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        for (Class<?> type : RESPONSE_RECORDS) {
            List<String> components = new ArrayList<>();
            for (var component : type.getRecordComponents()) {
                components.add(component.getName());
            }

            Object sample = sample(type);
            var json = mapper.readTree(mapper.writeValueAsString(sample));

            List<String> leaked = new ArrayList<>();
            json.fieldNames().forEachRemaining(name -> {
                if (!components.contains(name)) {
                    leaked.add(name);
                }
            });

            assertThat(leaked)
                    .as("%s 의 파생 메서드가 응답에 샜습니다. @JsonIgnore 를 붙이세요", type.getSimpleName())
                    .isEmpty();
        }
    }

    /** 성분을 전부 {@code null} 로 채운 표본. 값이 무엇이든 키 목록은 같다. */
    private Object sample(Class<?> type) throws Exception {
        var components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            args[i] = types[i] == boolean.class ? false : null;
        }
        return type.getDeclaredConstructor(types).newInstance(args);
    }
}
