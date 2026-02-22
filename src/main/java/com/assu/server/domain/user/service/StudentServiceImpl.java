package com.assu.server.domain.user.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.assu.server.domain.notification.repository.NotificationRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
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
			// 1. partnershipUsage의 paperContentId 로 paperContent 조회
			PaperContent paperContent = paperContentRepository.findById(u.getContentId())
				.orElse(null);

			// 2. store 추출
			Store store = (paperContent != null) ? paperContent.getPaper().getStore() : null;

			// 3. 날짜 포맷팅
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
		LocalDate today = LocalDate.now();

		List<UserPaper> userPapers = userPaperRepository.findActivePartnershipsByStudentId(memberId, today);

		List<StudentResponseDTO.UsablePartnershipDTO> result = userPapers.stream().map(up -> {
			Paper paper = up.getPaper();
			PaperContent content = up.getPaperContent();
			Store store = paper.getStore();

			String adminName = (paper.getAdmin() != null) ? paper.getAdmin().getName() : null;
			String partnerName = (store != null) ? store.getName() : null;

			// 카테고리 결정 로직 그대로
			String finalCategory = null;
			String note = null;
			if (content != null) {
				if(content.getNote() != null){
					note = content.getNote();
				}
				if (content.getCategory() != null) {
					finalCategory = content.getCategory();
				} else if (content.getOptionType() == OptionType.SERVICE) {
					List<Goods> goods = goodsRepository.findByContentId(content.getId());
					if (!goods.isEmpty()) {
						finalCategory = goods.get(0).getBelonging();
					}
				}
			}

			return StudentResponseDTO.UsablePartnershipDTO.builder()
					.partnershipId(paper.getId())
					.adminName(adminName)
					.partnerName(partnerName)
					.note(note).paperId(content != null? content.getPaper().getId(): null)
					.criterionType(content != null ? content.getCriterionType() : null)
					.optionType(content != null ? content.getOptionType() : null)
					.people(content != null ? content.getPeople() : null)
					.cost(content != null ? content.getCost() : null)
					.category(finalCategory)
					.discountRate(content != null ? content.getDiscount() : null)
					.build();
		}).toList();

		return Boolean.FALSE.equals(all) ? result.stream().limit(2).toList() : result;
	}

	@Transactional
	public void syncUserPapersForStudent(Long studentId) {
		Student student = studentRepository.findById(studentId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		// 1. 학생 기준으로 admin 찾기
		List<Admin> admins = adminRepository.findMatchingAdmins(
				student.getUniversity(),
				student.getDepartment(),
				student.getMajor()
		);

		if (admins.isEmpty()) {
			return;
		}

		List<Long> adminIds = admins.stream().map(Admin::getId).toList();
		LocalDate today = LocalDate.now();

		// 2. admin들이 만든 오늘 유효한 paper 조회
		List<Paper> papers = paperRepository.findActivePapersByAdminIds(
				adminIds,
				today,
				ActivationStatus.ACTIVE
		);

		// 3. user_paper에 없으면 넣기
		for (Paper paper : papers) {
			boolean exists = userPaperRepository.existsByStudentIdAndPaperId(studentId, paper.getId());
			if (exists) continue;

			PaperContent latestContent = paperContentRepository
					.findTopByPaperIdOrderByIdDesc(paper.getId())
					.orElse(null);

			UserPaper up = UserPaper.builder()
					.paper(paper)
					.paperContent(latestContent)
					.student(student)
					.build();

			userPaperRepository.save(up);
		}
	}
	@Transactional
	public void addStamp(Long memberId) {
		Student student = studentRepository.findById(memberId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		student.setStamp();

		if (student.getStamp() == 10) {
			notificationCommandService.sendStamp(memberId);

		}
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

