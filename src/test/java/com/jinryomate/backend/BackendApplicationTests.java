package com.jinryomate.backend;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@SpringBootTest
class BackendApplicationTests {

    @Test
    void 스프링_컨텍스트가_뜬다() {
    }
}
