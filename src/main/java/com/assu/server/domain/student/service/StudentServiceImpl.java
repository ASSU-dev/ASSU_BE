package com.assu.server.domain.student.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.notification.service.NotificationCommandService;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.dto.PaperContentResponseDTO;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
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
import com.assu.server.infra.discord.DiscordNotifier;
import com.assu.server.infra.s3.ImageCdnManager;
import com.assu.server.infra.s3.ImageSize;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {
	private final StudentRepository studentRepository;
	private final UserPaperRepository userPaperRepository;
	private final PaperContentRepository paperContentRepository;
	private final PartnershipUsageRepository partnershipUsageRepository;
	private final StampEventApplicantRepository stampEventApplicantRepository;
	private final AdminRepository adminRepository;
	private final PaperRepository paperRepository;
	private final NotificationCommandService notificationCommandService;
	private final MemberRepository memberRepository;
	private final HomeCurationRepository homeCurationRepository;
	private final HomeCurationItemRepository homeCurationItemRepository;
	private final StoreRepository storeRepository;
	private final ImageCdnManager imageCdnManager;
	private final TransactionTemplate transactionTemplate;
	private final DiscordNotifier discordNotifier;

	private static final int HOME_RECOMMEND_STORE_COUNT = 5;
	private static final int USABLE_PARTNERSHIP_PREVIEW_SIZE = 2;
	private static final String DEFAULT_DISCOUNT_DESCRIPTION = "제휴 혜택 제공";

    @Override
    @Transactional(readOnly = true)
    public StudentResponseDTO.CheckStampResponseDTO getStamp(Long memberId) {
        Student student = studentRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

        return StudentConverter.checkStampResponseDTO(student, "스탬프 조회 성공");
    }


	@Override
	@Transactional(readOnly=true)
	public StudentResponseDTO.MyPartnership getMyPartnership(Long studentId, int year, int month) {
		if (month < 1 || month > 12) {
			return new StudentResponseDTO.MyPartnership(0, List.of());
		}
		LocalDate from = LocalDate.of(year, month, 1);
		List<PartnershipUsage> usages =
			partnershipUsageRepository.findByStudentIdAndDateRange(studentId, from, from.plusMonths(1));

		Map<Long, Store> storeByContentId = loadStoresByContentId(usages);
		List<StudentResponseDTO.UsageDetail> details = usages.stream()
			.map(u -> toUsageDetail(u, storeByContentId.get(u.getContentId())))
			.toList();

		return new StudentResponseDTO.MyPartnership(
			usages.size(),
			details
		);
	}


	@Override
	@Transactional(readOnly = true)
	public Page<StudentResponseDTO.UsageDetail> getUnreviewedUsage(Long memberId, Pageable pageable) {
		// 프론트에서 1-based 페이지를 보낸 경우 0-based 로 보정
		pageable = PageRequest.of(
			Math.max(pageable.getPageNumber() - 1, 0),
			pageable.getPageSize(),
			pageable.getSort()
		);

		Page<PartnershipUsage> contentList =
			partnershipUsageRepository.findByUnreviewedUsage(memberId, pageable);

		Map<Long, Store> storeByContentId = loadStoresByContentId(contentList.getContent());
		return contentList.map(u -> toUsageDetail(u, storeByContentId.get(u.getContentId())));
	}

	private Map<Long, Store> loadStoresByContentId(List<PartnershipUsage> usages) {
		Set<Long> contentIds = usages.stream()
			.map(PartnershipUsage::getContentId)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Store> storeByContentId = new HashMap<>();
		for (PaperContent content : paperContentRepository.findAllWithPaperAndStoreByIdIn(contentIds)) {
			if (content.getPaper() != null && content.getPaper().getStore() != null) {
				storeByContentId.put(content.getId(), content.getPaper().getStore());
			}
		}
		return storeByContentId;
	}

	private StudentResponseDTO.UsageDetail toUsageDetail(PartnershipUsage usage, Store store) {
		String formatDate = usage.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
		return new StudentResponseDTO.UsageDetail(
			usage.getAdminName(),
			usage.getId(),
			usage.getPlace(),
			(store != null && store.getPartner() != null) ? store.getPartner().getId() : null,
			store != null ? store.getId() : null,
			formatDate,
			usage.getPartnershipContent(),
			usage.getIsReviewed()
		);
	}

	@Override
	@Transactional(readOnly = true)
	public List<StudentResponseDTO.UsablePartnershipDTO> getUsablePartnership(Long memberId, Boolean all, StoreCategory storeCategory, Long adminId) {
		List<UserPaper> userPapers = loadUsableUserPapers(memberId, all, storeCategory, adminId);

		Map<Long, Long> countByPaperId = userPapers.stream()
				.collect(Collectors.groupingBy(up -> up.getPaper().getId(), Collectors.counting()));

		List<UserPaper> representatives = userPapers.stream()
				.collect(Collectors.toMap(
						up -> up.getPaper().getId(),
						up -> up,
						(a, b) -> a,
						LinkedHashMap::new
				))
				.values().stream()
				.limit(Boolean.FALSE.equals(all) ? USABLE_PARTNERSHIP_PREVIEW_SIZE : Long.MAX_VALUE)
				.toList();

		return representatives.stream().map(up -> {
			Paper paper = up.getPaper();
			PaperContent content = up.getPaperContent();
			Store store = paper.getStore();

			int extraCount = countByPaperId.get(paper.getId()).intValue() - 1;
			return StudentResponseDTO.UsablePartnershipDTO.builder()
					.partnershipId(paper.getId())
					.adminName(paper.getAdmin() != null ? paper.getAdmin().getName() : null)
					.partnerName(store != null ? store.getName() : null)
					.paperId(paper.getId())
					.storeId(store != null ? store.getId() : null)
					.extraCount(extraCount)
					.partnerProfileUrl(getPartnerProfileImageUrl(paper.getPartner(), ImageSize.THUMBNAIL))
					.benefitCondition(content.getBenefitCondition())
					.benefit(content.getBenefit())
					.caution(content.getCaution())
					.minPeople(content.getMinPeople())
					.build();
		}).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<StudentResponseDTO.RecommendCarouselDTO> getRecommendCarouselPartnership(Long memberId) {
		List<Long> shuffledStoreIds = new ArrayList<>(storeRepository.findDistinctActiveStoreIdsByStudentId(memberId));
		Collections.shuffle(shuffledStoreIds);
		List<Long> storeIds = shuffledStoreIds.subList(0, Math.min(10, shuffledStoreIds.size()));
		if (storeIds.isEmpty()) {
			return List.of();
		}

		Map<Long, Store> storeById = storeRepository.findAllWithPartnerByIdIn(storeIds).stream()
				.collect(Collectors.toMap(Store::getId, Function.identity()));
		List<Store> randomStores = storeIds.stream()
				.map(storeById::get)
				.filter(Objects::nonNull)
				.toList();
		Map<Long, PaperContent> contentByStoreId = findLatestContentByStoreId(storeIds);
		return randomStores.stream().map(store -> {
			PaperContent content = contentByStoreId.get(store.getId());

			return new StudentResponseDTO.RecommendCarouselDTO(
					getStoreProfileImageUrl(store, ImageSize.THUMBNAIL),
					store.getName(),
					store.getId(),
					content != null ? content.getBenefitCondition() : null,
					content != null ? content.getBenefit() : null
			);
		}).toList();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
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
		List<PaperContent> allContents = paperContentRepository.findByPaperIdInOrderByIdAsc(paperIds);

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
		log.info("[UserPaper] studentId={} synced={}", studentId, newUserPapers.size());
	}
	@Transactional
	public StudentResponseDTO.CheckStampResponseDTO addStamp(Long memberId) {
		Student student = studentRepository.findByIdForUpdate(memberId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		if (student.isStampedToday()) {
			throw new DatabaseException(ErrorStatus.STAMP_ALREADY_APPLIED_TODAY);
		}

		return applyStamp(student);
	}

	@Override
	@Transactional
	public void addStampIfNotStampedToday(Long memberId) {
		Student student = studentRepository.findByIdForUpdate(memberId)
				.orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_STUDENT));

		if (student.isStampedToday()) {
			return;
		}

		applyStamp(student);
	}

	private StudentResponseDTO.CheckStampResponseDTO applyStamp(Student student) {
		student.addStamp();
		String responseMessage = "스탬프가 적립되었습니다.";

		if (student.getStamp() >= 10) {
			StampEventApplicant applicant = StampEventApplicant.builder()
					.student(student)
					.appliedAt(LocalDateTime.now())
					.eventVersion("2026_SEASON_1")
					.build();
			stampEventApplicantRepository.save(applicant);
			try {
				notificationCommandService.sendStamp(student.getId());
			} catch (Exception e) {
				// 알림 전송 실패해도 스탬프 적립은 성공
			}
			discordNotifier.send("🎁 " + student.getName() + "(회원 ID: " + student.getId()
					+ ") 학생이 스탬프 10개를 모아 2026_SEASON_1 이벤트에 자동 응모되었습니다.");

			student.resetStamp();
			responseMessage = "스탬프 10개를 모아 자동 응모 되었습니다.";
		}
		return StudentResponseDTO.CheckStampResponseDTO.builder()
				.userId(student.getId())
				.stamp(student.getStamp())
				.message(responseMessage)
				.build();
	}

	@Override
	@Transactional(readOnly = true)
	public Page<StudentResponseDTO.StampEventApplicantDTO> getStampEventApplicants(String eventVersion, Pageable pageable) {
		return stampEventApplicantRepository.findByEventVersion(eventVersion, pageable)
				.map(StudentResponseDTO.StampEventApplicantDTO::from);
	}

	@Override
	public void syncUserPapersForAllStudents() {
		List<Long> studentIds = studentRepository.findAll().stream().map(Student::getId).toList();
		int failCount = 0;
		for (Long studentId : studentIds) {
			try {
				transactionTemplate.executeWithoutResult(status -> syncUserPapersForStudent(studentId));
			} catch (DataIntegrityViolationException e) {
				failCount++;
				log.warn("[UserPaper] 다른 동기화와 경합, 이미 저장됨 studentId={}", studentId);
			} catch (Exception e) {
				failCount++;
				log.error("[UserPaper] 동기화 실패 studentId={}", studentId, e);
			}
		}
		log.info("[UserPaper] 전체 동기화 완료 total={} failed={}", studentIds.size(), failCount);
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
			List<Store> activeStores = storeRepository.findTopRatedExcludingWithdrawnPartner(
					PageRequest.of(0, HOME_RECOMMEND_STORE_COUNT));
			Map<Long, String> discountByStoreId = resolveStoreDiscountDescriptions(
					activeStores.stream().map(Store::getId).toList());

			StudentHomeResponseDTO.FeaturedRecommendationDTO featuredDTO = null;
			if (!activeStores.isEmpty()) {
				Store fs = activeStores.get(0);
				String fsImg = getStoreProfileImageUrl(fs, ImageSize.MEDIUM);
				String fsDiscount = discountByStoreId.get(fs.getId());
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
					String sImg = getStoreProfileImageUrl(s, ImageSize.MEDIUM);
					String discount = discountByStoreId.get(s.getId());
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

		List<HomeCurationItem> items = homeCurationItemRepository.findByHomeCurationIdWithStoreAndPartner(curation.getId());
		Map<Long, String> discountByStoreId = resolveStoreDiscountDescriptions(
				collectStoreIdsWithoutCustomDiscount(curation, items));

		StudentHomeResponseDTO.FeaturedRecommendationDTO featuredDTO = null;
		if (curation.getFeaturedStore() != null) {
			Store fs = curation.getFeaturedStore();
			String fsImg = getStoreProfileImageUrl(fs, ImageSize.MEDIUM);
			String discount = curation.getFeaturedDiscountContent();
			if (discount == null || discount.isBlank()) {
				discount = discountByStoreId.get(fs.getId());
			}
			featuredDTO = StudentHomeResponseDTO.FeaturedRecommendationDTO.of(
					fs.getId(),
					fs.getName(),
					discount,
					fs.getStoreCategory() != null ? fs.getStoreCategory().name() : null,
					fsImg
			);
		}

		Map<Integer, List<HomeCurationItem>> itemsByGroup = items.stream()
				.collect(Collectors.groupingBy(HomeCurationItem::getGroupIndex));

		List<StudentHomeResponseDTO.CurationGroupDTO> curationLists = new ArrayList<>();
		for (int groupIdx = 1; groupIdx <= 2; groupIdx++) {
			List<HomeCurationItem> groupItems = itemsByGroup.getOrDefault(groupIdx, List.of());
			String groupTitle = groupItems.isEmpty() ? "추천 제휴 " + groupIdx : groupItems.get(0).getGroupTitle();

			List<StudentHomeResponseDTO.CurationStoreDTO> storeList = groupItems.stream()
					.map(item -> {
						Store s = item.getStore();
						String sImg = getStoreProfileImageUrl(s, ImageSize.MEDIUM);
						String discount = item.getCustomDiscountContent();
						if (discount == null || discount.isBlank()) {
							discount = discountByStoreId.get(s.getId());
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

	private Set<Long> collectStoreIdsWithoutCustomDiscount(HomeCuration curation, List<HomeCurationItem> items) {
		Set<Long> storeIds = new HashSet<>();
		Store featuredStore = curation.getFeaturedStore();
		if (featuredStore != null && isBlank(curation.getFeaturedDiscountContent())) {
			storeIds.add(featuredStore.getId());
		}
		for (HomeCurationItem item : items) {
			if (isBlank(item.getCustomDiscountContent())) {
				storeIds.add(item.getStore().getId());
			}
		}
		return storeIds;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String getStoreProfileImageUrl(Store store, ImageSize size) {
		return store == null ? null : getPartnerProfileImageUrl(store.getPartner(), size);
	}

	private String getPartnerProfileImageUrl(Partner partner, ImageSize size) {
		if (partner == null || partner.getMember() == null) {
			return null;
		}
		return imageCdnManager.generateUrl(partner.getMember().getProfileUrl(), size);
	}

	private List<UserPaper> loadUsableUserPapers(Long memberId, Boolean all, StoreCategory storeCategory, Long adminId) {
		if (!Boolean.FALSE.equals(all)) {
			return userPaperRepository.findActivePartnershipsByStudentId(memberId, storeCategory, adminId);
		}
		List<Long> paperIds = userPaperRepository.findActivePaperIdsByStudentId(
				memberId, storeCategory, adminId, PageRequest.of(0, USABLE_PARTNERSHIP_PREVIEW_SIZE));
		if (paperIds.isEmpty()) {
			return List.of();
		}
		return userPaperRepository.findByStudentIdAndPaperIdIn(memberId, paperIds);
	}

	private Map<Long, PaperContent> findLatestContentByStoreId(Collection<Long> storeIds) {
		if (storeIds.isEmpty()) {
			return Map.of();
		}
		return paperContentRepository.findLatestByStoreIdIn(storeIds).stream()
				.collect(Collectors.toMap(
						content -> content.getPaper().getStore().getId(),
						Function.identity(),
						(first, ignored) -> first
				));
	}

	private Map<Long, String> resolveStoreDiscountDescriptions(Collection<Long> storeIds) {
		Map<Long, PaperContent> contentByStoreId = findLatestContentByStoreId(storeIds);
		Map<Long, String> descriptions = new HashMap<>();
		for (Long storeId : storeIds) {
			PaperContent content = contentByStoreId.get(storeId);
			descriptions.put(storeId, content != null
					? PaperContentResponseDTO.toContentResponse(content).paperContent()
					: DEFAULT_DISCOUNT_DESCRIPTION);
		}
		return descriptions;
	}
}
