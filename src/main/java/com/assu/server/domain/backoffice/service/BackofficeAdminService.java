package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeAdminCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminCreateResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminFetchResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminUpdateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminUpdateResponseDTO;

public interface BackofficeAdminService {
	BackofficeAdminFetchResponseDTO fetchAdmin();

	BackofficeAdminCreateResponseDTO createAdmin(BackofficeAdminCreateRequestDTO req);

	BackofficeAdminUpdateResponseDTO updateAdmin(Long adminId, BackofficeAdminUpdateRequestDTO req);

	void deleteAdmin(Long adminId);
}

