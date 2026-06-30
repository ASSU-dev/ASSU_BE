package com.assu.server.domain.backoffice.bootstrap;

import com.assu.server.domain.backoffice.dto.BackofficeOperatorCreateRequestDTO;
import com.assu.server.domain.backoffice.repository.BackofficeUserRepository;
import com.assu.server.domain.backoffice.service.BackofficeOperatorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "backoffice.bootstrap.enabled", havingValue = "true")
public class BackofficeBootstrapInitializer {

    private final BackofficeUserRepository backofficeUserRepository;
    private final BackofficeOperatorService backofficeOperatorService;
    private final String bootstrapEmail;
    private final String bootstrapPassword;
    private final String bootstrapName;

    public BackofficeBootstrapInitializer(
            BackofficeUserRepository backofficeUserRepository,
            BackofficeOperatorService backofficeOperatorService,
            @Value("${backoffice.bootstrap.email:}") String bootstrapEmail,
            @Value("${backoffice.bootstrap.password:}") String bootstrapPassword,
            @Value("${backoffice.bootstrap.name:}") String bootstrapName
    ) {
        this.backofficeUserRepository = backofficeUserRepository;
        this.backofficeOperatorService = backofficeOperatorService;
        this.bootstrapEmail = bootstrapEmail;
        this.bootstrapPassword = bootstrapPassword;
        this.bootstrapName = bootstrapName;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapInitialOperator() {
        if (backofficeUserRepository.countAllBy() > 0) {
            return;
        }

        if (bootstrapEmail.isBlank() || bootstrapPassword.isBlank() || bootstrapName.isBlank()) {
            log.warn("Backoffice bootstrap enabled but credentials are incomplete. Skipping initial operator creation.");
            return;
        }

        backofficeOperatorService.createOperator(
                new BackofficeOperatorCreateRequestDTO(
                        bootstrapEmail,
                        bootstrapPassword,
                        bootstrapName
                )
        );
        log.info("Initial BACKOFFICE operator bootstrapped for email={}", bootstrapEmail);
    }
}
