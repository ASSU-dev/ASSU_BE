package com.assu.server.domain.admin.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.assu.server.domain.admin.dto.AdminResponseDTO;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.user.entity.enums.Department;
import com.assu.server.domain.user.entity.enums.Major;
import lombok.RequiredArgsConstructor;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.user.entity.enums.University;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final PartnerRepository partnerRepository;

	@Override
	@Transactional
	public List<Admin> findMatchingAdmins(University university, Department department, Major major){

		List<Admin> adminList = adminRepository.findMatchingAdmins(university, department, major);

		return adminList;
	}

    @Override
    @Transactional(readOnly = true)
    public AdminResponseDTO suggestRandomPartner(Long adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));

        long total = partnerRepository.countUnpartneredActiveByAdmin(admin.getId());
        if (total <= 0) {
            throw new DatabaseException(ErrorStatus.NO_AVAILABLE_PARTNER);
        }

        int offset = ThreadLocalRandom.current().nextInt((int)total);

        Partner picked = partnerRepository.findUnpartneredActiveByAdminWithOffset(admin.getId(), offset);
        if(picked == null) {
            throw new DatabaseException(ErrorStatus.NO_AVAILABLE_PARTNER);
        }

        return AdminResponseDTO.from(picked);
    }

}
