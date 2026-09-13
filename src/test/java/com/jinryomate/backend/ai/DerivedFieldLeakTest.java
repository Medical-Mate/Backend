package com.jinryomate.backend.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.Resource;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.util.ClassUtils;

/**
 * 응답에 실리는 레코드의 <b>파생 메서드가 필드로 새지 않는지</b> 본다.
 *
 * <p>Jackson 은 레코드의 {@code isXxx()} · {@code getXxx()} 를 게터로 보고 응답에 싣는다.
 * {@code isEmpty()} 하나 붙였다가 {@code "empty": false} 가 앱에 나갔다.
 *
 * <p><b>처음엔 레코드 이름을 목록으로 적어 뒀는데, 그게 못 막았다.</b> 목록에 없는
 * 새 레코드({@code Clinic})가 같은 사고를 냈다. 목록은 규칙이 아니다 — 새로 만드는
 * 사람이 목록에 넣는 걸 잊으면 그만이다.
 *
 * <p>그래서 <b>패키지를 훑는다.</b> {@code com.jinryomate.backend} 아래 모든 레코드가
 * 대상이고, 새 레코드를 만들면 자동으로 걸린다.
 */
class DerivedFieldLeakTest {

    private static final String BASE = "com.jinryomate.backend";

    @Test
    @DisplayName("레코드 성분이 아닌 메서드는 JSON 에 나가지 않는다")
    void 파생_메서드는_응답에_안_실린다() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        List<String> leaks = new ArrayList<>();
        int checked = 0;

        for (Class<?> type : records()) {
            Object sample;
            try {
                sample = sample(type);
            } catch (ReflectiveOperationException e) {
                // 정규 생성자가 값을 검증하는 레코드는 건너뛴다. 그런 것은 응답 DTO 가 아니다.
                continue;
            }
            checked++;

            // 이름이 아니라 개수로 본다. @JsonProperty("end_reason") 나
            // @JsonNaming 으로 키 이름을 바꾸는 수신용 DTO 가 여럿이라 이름 대조는
            // 오탐만 낸다. 파생 게터는 키를 하나 늘리므로 개수로 정확히 잡힌다.
            var json = mapper.readTree(mapper.writeValueAsString(sample));
            int components = type.getRecordComponents().length;

            if (json.size() != components) {
                List<String> keys = new ArrayList<>();
                json.fieldNames().forEachRemaining(keys::add);
                leaks.add("%s — 성분 %d개인데 키가 %d개입니다 %s"
                        .formatted(type.getSimpleName(), components, json.size(), keys));
            }
        }

        // 훑기가 조용히 0건이 되면 이 테스트는 아무것도 안 지킨다.
        assertThat(checked).as("검사한 레코드가 없습니다. 패키지 훑기가 깨졌습니다").isGreaterThan(5);

        assertThat(leaks)
                .as("파생 메서드가 응답에 샜습니다. @JsonIgnore 를 붙이세요")
                .isEmpty();
    }

    /** {@code com.jinryomate.backend} 아래의 모든 레코드. 중첩 레코드도 포함한다. */
    private Set<Class<?>> records() throws Exception {
        var resolver = new PathMatchingResourcePatternResolver();
        MetadataReaderFactory readers = new CachingMetadataReaderFactory(resolver);

        Set<Class<?>> found = new LinkedHashSet<>();
        Resource[] resources = resolver.getResources(
                "classpath*:" + ClassUtils.convertClassNameToResourcePath(BASE) + "/**/*.class");

        for (Resource resource : resources) {
            String name = readers.getMetadataReader(resource).getClassMetadata().getClassName();
            try {
                Class<?> type = Class.forName(name, false, getClass().getClassLoader());
                collect(type, found);
            } catch (Throwable ignored) {
                // 로딩 못 하는 클래스는 응답 DTO 가 아니다.
            }
        }
        return found;
    }

    private void collect(Class<?> type, Set<Class<?>> found) {
        if (type.isRecord()) {
            found.add(type);
        }
        for (Class<?> nested : type.getDeclaredClasses()) {
            collect(nested, found);
        }
    }

    /** 성분을 전부 비워 만든 표본. 값이 무엇이든 <b>키 목록</b>은 같다. */
    private Object sample(Class<?> type) throws ReflectiveOperationException {
        var components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];

        for (int i = 0; i < components.length; i++) {
            Class<?> t = components[i].getType();
            types[i] = t;
            args[i] = switch (t.getName()) {
                case "boolean" -> false;
                case "byte" -> (byte) 0;
                case "short" -> (short) 0;
                case "int" -> 0;
                case "long" -> 0L;
                case "float" -> 0f;
                case "double" -> 0d;
                case "char" -> ' ';
                default -> null;
            };
        }

        var constructor = type.getDeclaredConstructor(types);
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }
}
