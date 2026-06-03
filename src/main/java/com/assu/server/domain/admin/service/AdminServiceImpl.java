package com.assu.server.domain.admin.service;

import com.assu.server.domain.admin.dto.AdminResponseDTO;
import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.domain.common.entity.enums.Department;
import com.assu.server.domain.common.entity.enums.Major;
import com.assu.server.domain.common.entity.enums.University;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;
    private final PartnerRepository partnerRepository;

    @Override
    @Transactional
    public List<Admin> findMatchingAdmins(University university, Department department, Major major){
        return adminRepository.findMatchingAdmins(university, department, major);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminResponseDTO suggestRandomPartner(Long adminId) {

        Admin admin = adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));

        long total = partnerRepository.countUnpartneredActiveByAdmin(admin.getId(), com.assu.server.domain.common.enums.ActivationStatus.ACTIVE);
        if (total <= 0) {
            throw new DatabaseException(ErrorStatus.NO_AVAILABLE_PARTNER);
        }

        int offset = ThreadLocalRandom.current().nextInt((int)total);

        Pageable pageable = PageRequest.of(offset, 1);
        List<Partner> pickedList = partnerRepository.findUnpartneredActiveByAdminWithOffset(admin.getId(), com.assu.server.domain.common.enums.ActivationStatus.ACTIVE, pageable);

        if (pickedList.isEmpty()) {
            throw new DatabaseException(ErrorStatus.NO_AVAILABLE_PARTNER);
        }

        Partner picked = pickedList.get(0);

        return AdminResponseDTO.from(picked);
    }
}