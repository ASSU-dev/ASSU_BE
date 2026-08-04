package com.assu.server;

import com.assu.server.support.CommonMockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(CommonMockConfig.class)
class ServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
