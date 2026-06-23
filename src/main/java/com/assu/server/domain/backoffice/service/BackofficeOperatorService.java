package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeOperatorCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorResponseDTO;

import java.util.List;

public interface BackofficeOperatorService {
    BackofficeOperatorResponseDTO createOperator(BackofficeOperatorCreateRequestDTO request);

    List<BackofficeOperatorResponseDTO> listOperators();
}
