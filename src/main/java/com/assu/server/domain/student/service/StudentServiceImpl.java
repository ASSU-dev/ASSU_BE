package com.assu.server.domain.student.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.dto.PaperContentResponseDTO;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.partnership.repository.GoodsRepository;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.partnership.repository.PaperRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.entity.enums.StoreCategory;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.converter.StudentConverter;
import com.assu.server.domain.student.dto.StudentHomeResponseDTO;
import com.assu.server.domain.student.dto.StudentProfileResponseDTO;
import com.assu.server.domain.student.dto.StudentResponseDTO;
import com.assu.server.domain.student.entity.HomeCuration;
import com.assu.server.domain.student.entity.HomeCurationItem;
import com.assu.server.domain.student.entity.PartnershipUsage;
import com.assu.server.domain.student.entity.StampEventApplicant;
import com.assu.server.domain.student.entity.Student;
import com.assu.server.domain.student.entity.UserPaper;
import com.assu.server.domain.student.repository.HomeCurationItemRepository;
import com.assu.server.domain.student.repository.HomeCurationRepository;
import com.assu.server.domain.student.repository.PartnershipUsageRepository;
import com.assu.server.domain.student.repository.StampEventApplicantRepository;
import com.assu.server.domain.student.repository.StudentRepository;
import com.assu.server.domain.student.repository.UserPaperRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import com.assu.server.infra.s3.AmazonS3Manager;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
	private final MemberRepository memberRepository;
	private final HomeCurationRepository homeCurationRepository;
	private final HomeCurationItemRepository homeCurationItemRepository;
	private final StoreRepository storeRepository;
	private final AmazonS3Manager amazonS3Manager;
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
	public List<StudentResponseDTO.UsablePartnershipDTO> getUsablePartnership(Long memberId, Boolean all, StoreCategory storeCategory, Long adminId) {
		List<UserPaper> userPapers = userPaperRepository.findActivePartnershipsByStudentId(memberId, storeCategory, adminId);

		Map<Long, Long> countByPaperId = userPapers.stream()
				.collect(Collectors.groupingBy(up -> up.getPaper().getId(), Collectors.counting()));

		List<UserPaper> representatives = userPapers.stream()
				.collect(Collectors.toMap(
						up -> up.getPaper().getId(),
						up -> up,
						(a, b) -> a,
						LinkedHashMap::new
				))
				.values().stream().toList();

		List<Long> contentIds = representatives.stream()
				.map(up -> up.getPaperContent().getId())
				.toList();
		Map<Long, List<Goods>> goodsMap = goodsRepository.findByContentIdIn(contentIds).stream()
				.collect(Collectors.groupingBy(g -> g.getContent().getId()));

		List<StudentResponseDTO.UsablePartnershipDTO> result = representatives.stream().map(up -> {
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

			Partner partner = paper.getPartner();
			int extraCount = countByPaperId.get(paper.getId()).intValue() - 1;
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
.storeId(store != null ? store.getId() : null)
					.extraCount(extraCount)
					.partnerProfileUrl(partner != null && partner.getMember() != null ? partner.getMember().getProfileUrl() : null)
					.build();
		}).toList();

		return Boolean.FALSE.equals(all) ? result.stream().limit(2).toList() : result;
	}

	@Override
	@Transactional(readOnly = true)
	public List<StudentResponseDTO.RecommendCarouselDTO> getRecommendCarouselPartnership(Long memberId) {
		List<UserPaper> userPapers = userPaperRepository.findActivePartnershipsByStudentId(memberId, null, null);
		List<UserPaper> shuffled = new ArrayList<>(userPapers);
		Collections.shuffle(shuffled);
		List<UserPaper> randomUserPapers = shuffled.subList(0, Math.min(10, shuffled.size()));

		List<Long> contentIds = randomUserPapers.stream()
				.map(up -> up.getPaperContent().getId())
				.toList();
		Map<Long, List<Goods>> goodsMap = goodsRepository.findByContentIdIn(contentIds).stream()
				.collect(Collectors.groupingBy(g -> g.getContent().getId()));

		return randomUserPapers.stream().map(up -> {
			PaperContent content = up.getPaperContent();
			Store store = up.getPaper().getStore();
			Partner partner = up.getPaper().getPartner();

			List<Goods> goods = goodsMap.get(content.getId());
			String firstBelonging = (goods != null && !goods.isEmpty()) ? goods.get(0).getBelonging() : null;

			return new StudentResponseDTO.RecommendCarouselDTO(
					content.getCategory(),
					firstBelonging,
					partner != null && partner.getMember() != null ? partner.getMember().getProfileUrl() : null,
					store != null ? store.getName() : null,
					store != null ? store.getId() : null
			);
		}).toList();
	}

	@Transactional
	@Override
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

	@Override
	@Transactional(readOnly = true)
	public StudentProfileResponseDTO getStudentProfile(Long memberId) {
		Member member = memberRepository.findStudentWithProfileById(memberId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));
		return StudentProfileResponseDTO.from(member);
	}

	@Override
	@Transactional(readOnly = true)
	public StudentHomeResponseDTO getStudentHome(Long memberId) {
		Student student = studentRepository.findById(memberId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		HomeCuration curation = homeCurationRepository.findLatest()
				.orElse(null);

		String studentName = (student.getName() != null && !student.getName().isBlank()) ? student.getName() : "학생";

		if (curation == null) {
			List<Store> activeStores = storeRepository.findAll().stream()
					.sorted(Comparator.comparing(Store::getRate, Comparator.nullsLast(Comparator.reverseOrder())))
					.limit(5)
					.toList();

			StudentHomeResponseDTO.FeaturedRecommendationDTO featuredDTO = null;
			if (!activeStores.isEmpty()) {
				Store fs = activeStores.get(0);
				String fsImg = getStoreProfileImageUrl(fs);
				String fsDiscount = resolveStoreDiscountDescription(fs.getId());
				featuredDTO = StudentHomeResponseDTO.FeaturedRecommendationDTO.of(
						fs.getId(),
						fs.getName(),
						fsDiscount,
						fs.getStoreCategory() != null ? fs.getStoreCategory().name() : null,
						fsImg
				);
			}

			List<StudentHomeResponseDTO.CurationStoreDTO> g1Stores = new ArrayList<>();
			List<StudentHomeResponseDTO.CurationStoreDTO> g2Stores = new ArrayList<>();
			if (activeStores.size() > 1) {
				List<Store> rem = activeStores.subList(1, activeStores.size());
				for (int i = 0; i < rem.size(); i++) {
					Store s = rem.get(i);
					String sImg = getStoreProfileImageUrl(s);
					String discount = resolveStoreDiscountDescription(s.getId());
					var storeDTO = StudentHomeResponseDTO.CurationStoreDTO.of(
							s.getId(),
							s.getName(),
							discount,
							s.getStoreCategory() != null ? s.getStoreCategory().name() : null,
							sImg
					);
					if (i < 2) {
						g1Stores.add(storeDTO);
					} else if (i < 4) {
						g2Stores.add(storeDTO);
					}
				}
			}

			return StudentHomeResponseDTO.of(
					featuredDTO,
					studentName + "님을 위한 제휴",
					List.of(
							StudentHomeResponseDTO.CurationGroupDTO.of(1, "추천 제휴 1", g1Stores),
							StudentHomeResponseDTO.CurationGroupDTO.of(2, "추천 제휴 2", g2Stores)
					)
			);
		}

		String rawTitle = curation.getTitle();
		String formattedTitle = rawTitle != null ? rawTitle.replace("{name}", studentName) : studentName + "님을 위한 제휴";

		StudentHomeResponseDTO.FeaturedRecommendationDTO featuredDTO = null;
		if (curation.getFeaturedStore() != null) {
			Store fs = curation.getFeaturedStore();
			String fsImg = getStoreProfileImageUrl(fs);
			String discount = curation.getFeaturedDiscountContent();
			if (discount == null || discount.isBlank()) {
				discount = resolveStoreDiscountDescription(fs.getId());
			}
			featuredDTO = StudentHomeResponseDTO.FeaturedRecommendationDTO.of(
					fs.getId(),
					fs.getName(),
					discount,
					fs.getStoreCategory() != null ? fs.getStoreCategory().name() : null,
					fsImg
			);
		}

		List<HomeCurationItem> items = homeCurationItemRepository.findByHomeCurationIdWithStoreAndPartner(curation.getId());
		Map<Integer, List<HomeCurationItem>> itemsByGroup = items.stream()
				.collect(Collectors.groupingBy(HomeCurationItem::getGroupIndex));

		List<StudentHomeResponseDTO.CurationGroupDTO> curationLists = new ArrayList<>();
		for (int groupIdx = 1; groupIdx <= 2; groupIdx++) {
			List<HomeCurationItem> groupItems = itemsByGroup.getOrDefault(groupIdx, List.of());
			String groupTitle = groupItems.isEmpty() ? "추천 제휴 " + groupIdx : groupItems.get(0).getGroupTitle();

			List<StudentHomeResponseDTO.CurationStoreDTO> storeList = groupItems.stream()
					.map(item -> {
						Store s = item.getStore();
						String sImg = getStoreProfileImageUrl(s);
						String discount = item.getCustomDiscountContent();
						if (discount == null || discount.isBlank()) {
							discount = resolveStoreDiscountDescription(s.getId());
						}
						return StudentHomeResponseDTO.CurationStoreDTO.of(
								s.getId(),
								s.getName(),
								discount,
								s.getStoreCategory() != null ? s.getStoreCategory().name() : null,
								sImg
						);
					})
					.toList();

			curationLists.add(StudentHomeResponseDTO.CurationGroupDTO.of(groupIdx, groupTitle, storeList));
		}

		return StudentHomeResponseDTO.of(featuredDTO, formattedTitle, curationLists);
	}

	private String getStoreProfileImageUrl(Store store) {
		if (store == null || store.getPartner() == null || store.getPartner().getMember() == null) {
			return null;
		}
		String key = store.getPartner().getMember().getProfileUrl();
		if (key != null && !key.isBlank()) {
			return amazonS3Manager.generatePresignedUrl(key);
		}
		return null;
	}

	private String resolveStoreDiscountDescription(Long storeId) {
		if (storeId == null) {
			return "";
		}
		List<PaperContent> contents = paperContentRepository.findTopByStoreIdIn(List.of(storeId));
		if (!contents.isEmpty()) {
			PaperContent pc = contents.get(0);
			return PaperContentResponseDTO.toContentResponse(pc).paperContent();
		}
		return "제휴 혜택 제공";
	}
}


