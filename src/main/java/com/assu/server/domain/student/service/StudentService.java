package com.assu.server.domain.student.service;

import java.util.List;

import com.assu.server.domain.store.entity.enums.StoreCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.student.dto.StudentProfileResponseDTO;
import com.assu.server.domain.student.dto.StudentResponseDTO;

public interface StudentService {
	StudentResponseDTO.MyPartnership getMyPartnership(Long studentId, int year, int month);
    StudentResponseDTO.CheckStampResponseDTO getStamp(Long memberId);//조회
	Page<StudentResponseDTO.UsageDetail> getUnreviewedUsage(Long memberId, Pageable pageable);
	List<StudentResponseDTO.UsablePartnershipDTO> getUsablePartnership(Long memberId, Boolean all, StoreCategory storeCategory);
	void syncUserPapersForAllStudents();
	StudentResponseDTO.CheckStampResponseDTO addStamp(Long id);
	StudentProfileResponseDTO getStudentProfile(Long memberId);
}
