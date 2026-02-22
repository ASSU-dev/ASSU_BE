package com.assu.server.domain.user.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.assu.server.domain.user.dto.StudentResponseDTO;

public interface StudentService {
	StudentResponseDTO.MyPartnership getMyPartnership(Long studentId, int year, int month);
    StudentResponseDTO.CheckStampResponseDTO getStamp(Long memberId);//조회
	Page<StudentResponseDTO.UsageDetail> getUnreviewedUsage(Long memberId, Pageable pageable);
	List<StudentResponseDTO.UsablePartnershipDTO> getUsablePartnership(Long memberId, Boolean all);
	void syncUserPapersForAllStudents();
	void addStamp(Long id);
}
