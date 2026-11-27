package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.backoffice.dto.BackofficeCurationTitleUpdateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeAutoRecommendResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeCurationResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeHomeCurationUpdateRequestDTO;
import com.assu.server.domain.partnership.dto.PaperContentResponseDTO;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.repository.PaperContentRepository;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.domain.store.repository.StoreRepository;
import com.assu.server.domain.student.entity.HomeCuration;
import com.assu.server.domain.student.entity.HomeCurationItem;
import com.assu.server.domain.student.repository.HomeCurationItemRepository;
import com.assu.server.domain.student.repository.HomeCurationRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeHomeCurationServiceImpl implements BackofficeHomeCurationService {

    private final HomeCurationRepository homeCurationRepository;
    private final HomeCurationItemRepository homeCurationItemRepository;
    private final StoreRepository storeRepository;
    private final PaperContentRepository paperContentRepository;

    private static final String DEFAULT_TITLE = "{name}님을 위한 제휴";
    private static final String DEFAULT_DISCOUNT_DESCRIPTION = "제휴 혜택 제공";
    private static final int AUTO_RECOMMEND_STORE_COUNT = 5;

    @Override
    @Transactional(readOnly = true)
    public BackofficeHomeCurationResponseDTO getHomeCuration() {
        HomeCuration curation = homeCurationRepository.findLatest()
                .orElse(null);

        if (curation == null) {
            return BackofficeHomeCurationResponseDTO.of(
                    null,
                    DEFAULT_TITLE,
                    null,
                    List.of(
                            BackofficeHomeCurationResponseDTO.GroupDTO.of(1, "추천 제휴 1", List.of()),
                            BackofficeHomeCurationResponseDTO.GroupDTO.of(2, "추천 제휴 2", List.of())
                    )
            );
        }

        List<HomeCurationItem> items = homeCurationItemRepository.findByHomeCurationIdWithStoreAndPartner(curation.getId());
        return mapToBackofficeResponseDTO(curation, items);
    }

    @Override
    public BackofficeHomeCurationResponseDTO updateCurationTitle(BackofficeCurationTitleUpdateRequestDTO request) {
        HomeCuration curation = homeCurationRepository.findTopByOrderByIdDesc()
                .orElseGet(() -> homeCurationRepository.save(
                        HomeCuration.builder()
                                .title(request.title())
                                .build()
                ));

        curation.updateTitle(request.title());
        homeCurationRepository.save(curation);

        List<HomeCurationItem> items = homeCurationItemRepository.findByHomeCurationIdWithStoreAndPartner(curation.getId());
        return mapToBackofficeResponseDTO(curation, items);
    }

    @Override
    public BackofficeHomeCurationResponseDTO updateHomeCuration(BackofficeHomeCurationUpdateRequestDTO request) {
        validateCurationGroups(request.curationLists());

        Store featuredStore = storeRepository.findById(request.featuredStoreId())
                .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_STORE));

        HomeCuration curation = homeCurationRepository.findTopByOrderByIdDesc()
                .orElseGet(() -> HomeCuration.builder()
                        .title(request.title())
                        .build()
                );

        curation.updateTitle(request.title());
        curation.updateFeatured(featuredStore, request.featuredDiscountContent());

        curation.getItems().clear();

        for (BackofficeHomeCurationUpdateRequestDTO.GroupUpdateRequestDTO groupReq : request.curationLists()) {
            for (BackofficeHomeCurationUpdateRequestDTO.StoreItemRequestDTO storeReq : groupReq.stores()) {
                Store store = storeRepository.findById(storeReq.storeId())
                        .orElseThrow(() -> new GeneralException(ErrorStatus.NO_SUCH_STORE));

                HomeCurationItem item = HomeCurationItem.builder()
                        .homeCuration(curation)
                        .groupIndex(groupReq.groupIndex())
                        .groupTitle(groupReq.groupTitle() != null ? groupReq.groupTitle() : "추천 제휴 " + groupReq.groupIndex())
                        .store(store)
                        .customDiscountContent(storeReq.customDiscountContent())
                        .sortOrder(storeReq.sortOrder())
                        .build();

                curation.getItems().add(item);
            }
        }

        HomeCuration savedCuration = homeCurationRepository.save(curation);
        List<HomeCurationItem> items = homeCurationItemRepository.findByHomeCurationIdWithStoreAndPartner(savedCuration.getId());
        return mapToBackofficeResponseDTO(savedCuration, items);
    }

    private void validateCurationGroups(List<BackofficeHomeCurationUpdateRequestDTO.GroupUpdateRequestDTO> curationLists) {
        if (curationLists == null || curationLists.size() != 2) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }
        Set<Integer> groupIndices = curationLists.stream()
                .map(BackofficeHomeCurationUpdateRequestDTO.GroupUpdateRequestDTO::groupIndex)
                .collect(Collectors.toSet());
        if (!Set.of(1, 2).equals(groupIndices)) {
            throw new GeneralException(ErrorStatus._BAD_REQUEST);
        }

        for (BackofficeHomeCurationUpdateRequestDTO.GroupUpdateRequestDTO groupReq : curationLists) {
            if (groupReq.stores() == null || groupReq.stores().size() != 2) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST);
            }
            Set<Integer> sortOrders = groupReq.stores().stream()
                    .map(BackofficeHomeCurationUpdateRequestDTO.StoreItemRequestDTO::sortOrder)
                    .collect(Collectors.toSet());
            if (!Set.of(1, 2).equals(sortOrders)) {
                throw new GeneralException(ErrorStatus._BAD_REQUEST);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public BackofficeHomeAutoRecommendResponseDTO getAutoRecommendedCuration() {
        List<Store> candidateStores = storeRepository.findTopRated(PageRequest.of(0, AUTO_RECOMMEND_STORE_COUNT));

        if (candidateStores.isEmpty()) {
            return BackofficeHomeAutoRecommendResponseDTO.of(
                    DEFAULT_TITLE,
                    null,
                    List.of(
                            BackofficeHomeAutoRecommendResponseDTO.AutoGroupDTO.of(1, "추천 제휴 1", List.of()),
                            BackofficeHomeAutoRecommendResponseDTO.AutoGroupDTO.of(2, "추천 제휴 2", List.of())
                    )
            );
        }

        Map<Long, String> discountByStoreId = resolveStoreDiscountDescriptions(
                candidateStores.stream().map(Store::getId).toList());
        Store featuredStore = candidateStores.get(0);
        String featuredDiscount = discountByStoreId.get(featuredStore.getId());
        BackofficeHomeAutoRecommendResponseDTO.AutoFeaturedDTO featuredDTO = BackofficeHomeAutoRecommendResponseDTO.AutoFeaturedDTO.of(
                featuredStore.getId(),
                featuredStore.getName(),
                featuredDiscount,
                featuredStore.getStoreCategory() != null ? featuredStore.getStoreCategory().name() : null
        );

        List<Store> remainingStores = candidateStores.subList(1, candidateStores.size());
        List<BackofficeHomeAutoRecommendResponseDTO.AutoStoreDTO> group1Stores = new ArrayList<>();
        List<BackofficeHomeAutoRecommendResponseDTO.AutoStoreDTO> group2Stores = new ArrayList<>();

        for (int i = 0; i < remainingStores.size(); i++) {
            Store store = remainingStores.get(i);
            String discount = discountByStoreId.get(store.getId());
            String category = store.getStoreCategory() != null ? store.getStoreCategory().name() : null;

            if (i < 2) {
                group1Stores.add(BackofficeHomeAutoRecommendResponseDTO.AutoStoreDTO.of(
                        store.getId(),
                        store.getName(),
                        discount,
                        category,
                        i + 1
                ));
            } else if (i < 4) {
                group2Stores.add(BackofficeHomeAutoRecommendResponseDTO.AutoStoreDTO.of(
                        store.getId(),
                        store.getName(),
                        discount,
                        category,
                        i - 1
                ));
            }
        }

        List<BackofficeHomeAutoRecommendResponseDTO.AutoGroupDTO> groups = List.of(
                BackofficeHomeAutoRecommendResponseDTO.AutoGroupDTO.of(1, "추천 제휴 1", group1Stores),
                BackofficeHomeAutoRecommendResponseDTO.AutoGroupDTO.of(2, "추천 제휴 2", group2Stores)
        );

        return BackofficeHomeAutoRecommendResponseDTO.of(DEFAULT_TITLE, featuredDTO, groups);
    }

    private BackofficeHomeCurationResponseDTO mapToBackofficeResponseDTO(HomeCuration curation, List<HomeCurationItem> items) {
        Map<Long, String> discountByStoreId = resolveStoreDiscountDescriptions(
                collectStoreIdsWithoutCustomDiscount(curation, items));

        BackofficeHomeCurationResponseDTO.FeaturedDTO featuredDTO = null;
        if (curation.getFeaturedStore() != null) {
            Store fs = curation.getFeaturedStore();
            String discount = curation.getFeaturedDiscountContent();
            if (discount == null || discount.isBlank()) {
                discount = discountByStoreId.get(fs.getId());
            }
            featuredDTO = BackofficeHomeCurationResponseDTO.FeaturedDTO.of(
                    fs.getId(),
                    fs.getName(),
                    discount,
                    fs.getStoreCategory() != null ? fs.getStoreCategory().name() : null
            );
        }

        Map<Integer, List<HomeCurationItem>> itemsByGroup = items.stream()
                .collect(Collectors.groupingBy(HomeCurationItem::getGroupIndex));

        List<BackofficeHomeCurationResponseDTO.GroupDTO> groupDTOList = new ArrayList<>();
        for (int groupIdx = 1; groupIdx <= 2; groupIdx++) {
            List<HomeCurationItem> groupItems = itemsByGroup.getOrDefault(groupIdx, List.of());
            String groupTitle = groupItems.isEmpty() ? "추천 제휴 " + groupIdx : groupItems.get(0).getGroupTitle();

            List<BackofficeHomeCurationResponseDTO.StoreItemDTO> storeList = groupItems.stream()
                    .map(item -> {
                        Store s = item.getStore();
                        String discount = item.getCustomDiscountContent();
                        if (discount == null || discount.isBlank()) {
                            discount = discountByStoreId.get(s.getId());
                        }
                        return BackofficeHomeCurationResponseDTO.StoreItemDTO.of(
                                s.getId(),
                                s.getName(),
                                discount,
                                s.getStoreCategory() != null ? s.getStoreCategory().name() : null,
                                item.getSortOrder()
                        );
                    })
                    .toList();

            groupDTOList.add(BackofficeHomeCurationResponseDTO.GroupDTO.of(groupIdx, groupTitle, storeList));
        }

        return BackofficeHomeCurationResponseDTO.of(
                curation.getId(),
                curation.getTitle(),
                featuredDTO,
                groupDTOList
        );
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

    private Map<Long, String> resolveStoreDiscountDescriptions(Collection<Long> storeIds) {
        if (storeIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, PaperContent> contentByStoreId = paperContentRepository.findLatestByStoreIdIn(storeIds).stream()
                .collect(Collectors.toMap(
                        content -> content.getPaper().getStore().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
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
