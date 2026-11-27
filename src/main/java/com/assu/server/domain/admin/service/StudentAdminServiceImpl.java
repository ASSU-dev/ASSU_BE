package com.assu.server.domain.admin.service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.admin.dto.StoreUsageWithPaperDTO;
import com.assu.server.domain.admin.dto.StudentAdminResponseDTO;
import com.assu.server.domain.admin.repository.StudentAdminRepository;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class StudentAdminServiceImpl implements StudentAdminService {
    private final StudentAdminRepository studentAdminRepository;
    private final AdminRepository adminRepository;
    private final PaperRepository paperRepository;

    @Override
    public StudentAdminResponseDTO.CountAdminAuthResponseDTO getCountAdminAuth(Long memberId) {
        Admin admin = getAdminOrThrow(memberId);
        Long total = studentAdminRepository.countAllByAdminId(memberId);

        return StudentAdminResponseDTO.CountAdminAuthResponseDTO.from(memberId, total, admin.getName());
    }

    @Override
    public StudentAdminResponseDTO.NewCountAdminResponseDTO getNewStudentCountAdmin(Long memberId) {
        Admin admin = getAdminOrThrow(memberId);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        Long total = studentAdminRepository.countTodayUsersByAdmin(memberId, startOfDay, endOfDay);

        return StudentAdminResponseDTO.NewCountAdminResponseDTO.from(memberId, total, admin.getName());
    }

    @Override
    public StudentAdminResponseDTO.CountUsagePersonResponseDTO getCountUsagePerson(Long memberId) {
        Admin admin = getAdminOrThrow(memberId);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);

        Long total = studentAdminRepository.countTodayUsersByAdmin(memberId, startOfDay, endOfDay);

        return StudentAdminResponseDTO.CountUsagePersonResponseDTO.from(memberId, total, admin.getName());
    }

    @Override
    public StudentAdminResponseDTO.CountUsageResponseDTO getCountUsage(Long memberId) {
        Admin admin = getAdminOrThrow(memberId);

        List<StoreUsageWithPaperDTO> storeUsages =
                studentAdminRepository.findUsageByStoreWithPaper(memberId);

        if (storeUsages.isEmpty()) {
            throw new DatabaseException(ErrorStatus.NO_USAGE_DATA);
        }

        StoreUsageWithPaperDTO top = storeUsages.get(0);

        Paper paper = paperRepository.findById(top.paperId())
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_PAPER_FOR_STORE));

        return StudentAdminResponseDTO.CountUsageResponseDTO.from(admin, paper, top.usageCount());
    }

    @Override
    public StudentAdminResponseDTO.CountUsageListResponseDTO getCountUsageList(Long memberId) {
        Admin admin = getAdminOrThrow(memberId);

        List<StoreUsageWithPaperDTO> storeUsages =
                studentAdminRepository.findUsageByStoreWithPaper(memberId);

        if (storeUsages.isEmpty()) {
            return StudentAdminResponseDTO.CountUsageListResponseDTO.from(List.of());
        }

        List<Long> paperIds = storeUsages.stream()
                .map(StoreUsageWithPaperDTO::paperId)
                .toList();

        Map<Long, Paper> paperMap = paperRepository.findAllById(paperIds).stream()
                .collect(Collectors.toMap(Paper::getId, paper -> paper));

        List<StudentAdminResponseDTO.CountUsageResponseDTO> items = storeUsages.stream().map(row -> {
            Paper paper = paperMap.get(row.paperId());
            if (paper == null) {
                throw new DatabaseException(ErrorStatus.NO_PAPER_FOR_STORE);
            }
            return StudentAdminResponseDTO.CountUsageResponseDTO.from(admin, paper, row.usageCount());
        }).toList();

        return StudentAdminResponseDTO.CountUsageListResponseDTO.from(items);
    }

    private Admin getAdminOrThrow(Long adminId) {
        return adminRepository.findById(adminId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_ADMIN));
    }
}