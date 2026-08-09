package com.assu.server.infra.firebase;

import com.google.firebase.FirebaseApp;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnFirebaseEnabled
public class FirebaseInitLogger {

    private static boolean logged = false;

    @PostConstruct
    public void printFcmInitOnce() {
        if (logged) return; // 이미 찍었으면 무시

        try {
            FirebaseApp app = FirebaseApp.getInstance();
            var options = app.getOptions();

            log.info("[FCM_INIT] projectId={}",
                    options.getProjectId());

            logged = true;

        } catch (Exception e) {
            log.error("[FCM_INIT] FirebaseApp 초기화 실패", e);
        }
    }
}