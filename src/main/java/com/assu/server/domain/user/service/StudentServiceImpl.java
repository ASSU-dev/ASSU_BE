package com.assu.server.domain.user.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.user.entity.StampEventApplicant;
import com.assu.server.domain.user.repository.StampEventApplicantRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.user.converter.StudentConverter;
import com.assu.server.domain.user.dto.StudentResponseDTO;
import com.assu.server.domain.user.entity.PartnershipUsage;
import com.assu.server.domain.user.entity.Student;
import com.assu.server.domain.user.entity.UserPaper;
import com.assu.server.domain.user.repository.PartnershipUsageRepository;
import com.assu.server.domain.user.repository.StudentRepository;
import com.assu.server.domain.user.repository.UserPaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {
	private final StudentRepository studentRepository;
	private final UserPaperRepository userPaperRepository;
	private final PaperContentRepository paperContentRepository;
	private final PartnershipUsageRepository partnershipUsageRepository;
	private final StampEventApplicantRepository stampEventApplicantRepository;
	private final GoodsRepository goodsRepository;
	private final AdminRepository adminRepository;
	private final PaperRepository paperRepository;
	private final NotificationCommandService notificationCommandService;
    @Override
    @Transactional
    public StudentResponseDTO.CheckStampResponseDTO getStamp(Long memberId) {
        Student student = studentRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

        return StudentConverter.checkStampResponseDTO(student, "스탬프 조회 성공");
    }


	@Override
	@Transactional(readOnly=true)
	public StudentResponseDTO.MyPartnership getMyPartnership(Long studentId, int year, int month) {

		List<PartnershipUsage> usages =
			partnershipUsageRepository.findByYearAndMonth(studentId, year, month);

		List<StudentResponseDTO.UsageDetail> details =
			usages.stream()
				.map(u -> {
					PaperContent paperContent = paperContentRepository
						.findById(u.getContentId())
						.orElse(null);

					Store store = paperContent != null
						? paperContent.getPaper().getStore()
						: null;

					String formatDate = u.getCreatedAt()
						.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

					return new StudentResponseDTO.UsageDetail(
						u.getAdminName(),
						u.getId(),
						u.getPlace(),
						store != null ? store.getPartner().getId() : null,
						store != null ? store.getId() : null,
						formatDate,
						u.getPartnershipContent(),
						u.getIsReviewed()
					);
				})
				.toList();

		return new StudentResponseDTO.MyPartnership(
			usages.size(),
			details
		);
	}


	@Override
	@Transactional
	public Page<StudentResponseDTO.UsageDetail> getUnreviewedUsage(Long memberId, Pageable pageable) {
		// 프론트에서 1-based 페이지를 보낸 경우 0-based 로 보정
		pageable = PageRequest.of(
			Math.max(pageable.getPageNumber() - 1, 0),
			pageable.getPageSize(),
			pageable.getSort()
		);

		Page<PartnershipUsage> contentList =
			partnershipUsageRepository.findByUnreviewedUsage(memberId, pageable);

		return contentList.map(u -> {
			PaperContent paperContent = paperContentRepository.findById(u.getContentId())
				.orElse(null);

			Store store = (paperContent != null) ? paperContent.getPaper().getStore() : null;

			LocalDateTime ld = u.getCreatedAt();
			String formatDate = ld.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));

			return new StudentResponseDTO.UsageDetail(
				u.getAdminName(),
				u.getId(),
				u.getPlace(),
				(store != null && store.getPartner() != null) ? store.getPartner().getId() : null,
				 (store!= null)? store.getId(): null, formatDate,
				u.getPartnershipContent(),
				u.getIsReviewed());

		});
	}

	@Override
	public List<StudentResponseDTO.UsablePartnershipDTO> getUsablePartnership(Long memberId, Boolean all) {
		List<UserPaper> userPapers = userPaperRepository.findActivePartnershipsByStudentId(memberId, LocalDate.now());

		// Goods 일괄 조회 (N+1 방지)
		List<Long> contentIds = userPapers.stream()
				.map(up -> up.getPaperContent().getId())
				.toList();
		Map<Long, List<Goods>> goodsMap = goodsRepository.findByContentIdIn(contentIds).stream()
				.collect(Collectors.groupingBy(g -> g.getContent().getId()));

		List<StudentResponseDTO.UsablePartnershipDTO> result = userPapers.stream().map(up -> {
			Paper paper = up.getPaper();
			PaperContent content = up.getPaperContent();
			Store store = paper.getStore();

			String finalCategory = content.getCategory();
			if (finalCategory == null && content.getOptionType() == OptionType.SERVICE) {
				List<Goods> goods = goodsMap.get(content.getId());
				if (goods != null && !goods.isEmpty()) {
					finalCategory = goods.get(0).getBelonging();
				}
			}

			return StudentResponseDTO.UsablePartnershipDTO.builder()
					.partnershipId(paper.getId())
					.adminName(paper.getAdmin() != null ? paper.getAdmin().getName() : null)
					.partnerName(store != null ? store.getName() : null)
					.note(content.getNote())
					.paperId(paper.getId())
					.criterionType(content.getCriterionType())
					.optionType(content.getOptionType())
					.people(content.getPeople())
					.cost(content.getCost())
					.category(finalCategory)
					.discountRate(content.getDiscount())
					.build();
		}).toList();

		return Boolean.FALSE.equals(all) ? result.stream().limit(2).toList() : result;
	}

	@Transactional
	public void syncUserPapersForStudent(Long studentId) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		List<Admin> admins = adminRepository.findMatchingAdmins(
				student.getUniversity(),
				student.getDepartment(),
				student.getMajor()
		);

		if (admins.isEmpty()) return;

		List<Long> adminIds = admins.stream().map(Admin::getId).toList();
		List<Paper> papers = paperRepository.findActivePapersByAdminIds(
				adminIds, LocalDate.now(), ActivationStatus.ACTIVE
		);

		if (papers.isEmpty()) return;

		List<Long> paperIds = papers.stream().map(Paper::getId).toList();
		List<PaperContent> allContents = paperContentRepository.findByPaperIdIn(paperIds);

		// 기존 UserPaper 한 번에 조회 (N+1 방지)
		List<UserPaper> existing = userPaperRepository.findByStudentId(studentId);
		Set<String> existingKeys = existing.stream()
				.map(up -> up.getPaper().getId() + "_" + up.getPaperContent().getId())
				.collect(Collectors.toSet());

		List<UserPaper> newUserPapers = allContents.stream()
				.filter(content -> !existingKeys.contains(content.getPaper().getId() + "_" + content.getId()))
				.map(content -> UserPaper.builder()
						.paper(content.getPaper())
						.paperContent(content)
						.student(student)
						.build())
				.toList();

		if (!newUserPapers.isEmpty()) {
			userPaperRepository.saveAll(newUserPapers);
		}
	}
	@Transactional
	public StudentResponseDTO.CheckStampResponseDTO addStamp(Long memberId) {
		Student student = studentRepository.findById(memberId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		student.setStamp();
		String responseMessage = "스탬프가 적립되었습니다.";

		if (student.getStamp() >= 10) {
			StampEventApplicant applicant = StampEventApplicant.builder()
					.student(student)
					.appliedAt(LocalDateTime.now())
					.eventVersion("2026_SEASON_1")
					.build();
			stampEventApplicantRepository.save(applicant);
			try {
				notificationCommandService.sendStamp(memberId);
			} catch (Exception e) {
				// 알림 전송 실패해도 스탬프 적립은 성공
			}

			student.resetStamp();
			responseMessage = "스탬프 10개를 모아 자동 응모 되었습니다.";
		}
		return StudentResponseDTO.CheckStampResponseDTO.builder()
				.userId(student.getId())
				.stamp(student.getStamp())
				.message(responseMessage)
				.build();
	}

	/**
	 * 전체 학생에 대해 일괄로 user_paper 채워 넣는 메서드
	 * (스케줄러에서 이거만 호출하면 됨)
	 */
	@Transactional
	@Override
	public void syncUserPapersForAllStudents() {
		List<Student> students = studentRepository.findAll();
		for (Student s : students) {
			syncUserPapersForStudent(s.getId());
		}
	}
}

