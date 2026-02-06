package com.assu.server.domain.map.converter;

import java.util.List;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.map.dto.MapResponseDTO;
import com.assu.server.domain.partner.entity.Partner;
import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;
import com.assu.server.domain.store.entity.Store;
import com.assu.server.infra.s3.AmazonS3Manager;

public class MapConverter {

	public static MapResponseDTO.PartnerMapResponseDTO toPartnerMapResponseDTO(
			Partner partner,
			Paper activePaper,
			AmazonS3Manager s3Manager
	) {
		final String key = (partner.getMember() != null) ? partner.getMember().getProfileUrl() : null;
		final String profileUrl = (key != null && !key.isBlank()) ? s3Manager.generatePresignedUrl(key) : null;

		return MapResponseDTO.PartnerMapResponseDTO.builder()
				.partnerId(partner.getId())
				.name(partner.getName())
				.address(partner.getAddress() != null ? partner.getAddress() : partner.getDetailAddress())
				.isPartnered(activePaper != null)
				.partnershipId(activePaper != null ? activePaper.getId() : null)
				.partnershipStartDate(activePaper != null ? activePaper.getPartnershipPeriodStart() : null)
				.partnershipEndDate(activePaper != null ? activePaper.getPartnershipPeriodEnd() : null)
				.latitude(partner.getLatitude())
				.longitude(partner.getLongitude())
				.profileUrl(profileUrl)
				.phoneNumber(partner.getMember().getPhoneNum())
				.build();
	}

	public static MapResponseDTO.AdminMapResponseDTO toAdminMapResponseDTO(
			Admin admin,
			Paper activePaper,
			AmazonS3Manager s3Manager
	) {
		final String key = (admin.getMember() != null) ? admin.getMember().getProfileUrl() : null;
		final String profileUrl = (key != null && !key.isBlank()) ? s3Manager.generatePresignedUrl(key) : null;

		return MapResponseDTO.AdminMapResponseDTO.builder()
				.adminId(admin.getId())
				.name(admin.getName())
				.address(admin.getOfficeAddress() != null ? admin.getOfficeAddress() : admin.getDetailAddress())
				.isPartnered(activePaper != null)
				.partnershipId(activePaper != null ? activePaper.getId() : null)
				.partnershipStartDate(activePaper != null ? activePaper.getPartnershipPeriodStart() : null)
				.partnershipEndDate(activePaper != null ? activePaper.getPartnershipPeriodEnd() : null)
				.latitude(admin.getLatitude())
				.longitude(admin.getLongitude())
				.profileUrl(profileUrl)
				.phoneNumber(admin.getMember().getPhoneNum())
				.build();
	}

	public static MapResponseDTO.StoreMapResponseDTO toStoreMapResponseDTO(
			Store store,
			PaperContent content,
			Long adminId,
			String adminName,
			AmazonS3Manager s3Manager
	) {
		final boolean hasPartner = (store.getPartner() != null);

		final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
				? store.getPartner().getMember().getProfileUrl()
				: null;
		final String profileUrl = (key != null ? s3Manager.generatePresignedUrl(key) : null);

		final String phoneNumber = (store.getPartner() != null
				&& store.getPartner().getMember() != null
				&& store.getPartner().getMember().getPhoneNum() != null)
				? store.getPartner().getMember().getPhoneNum()
				: "";

		return MapResponseDTO.StoreMapResponseDTO.builder()
				.storeId(store.getId())
				.adminId(adminId)
				.adminName(adminName)
				.name(store.getName())
				.address(store.getAddress() != null ? store.getAddress() : store.getDetailAddress())
				.rate(store.getRate())
				.criterionType(content != null ? content.getCriterionType() : null)
				.optionType(content != null ? content.getOptionType() : null)
				.people(content != null ? content.getPeople() : null)
				.cost(content != null ? content.getCost() : null)
				.category(content != null ? content.getCategory() : null)
				.discountRate(content != null ? content.getDiscount() : null)
				.hasPartner(hasPartner)
				.latitude(store.getLatitude())
				.longitude(store.getLongitude())
				.profileUrl(profileUrl)
				.phoneNumber(phoneNumber)
				.build();
	}

	public static MapResponseDTO.StoreMapResponseDTO toStoreMapResponseDTOForSearch(
			Store store,
			PaperContent content,
			String finalCategory,
			Long adminId,
			String adminName,
			AmazonS3Manager s3Manager
	) {
		final boolean hasPartner = store.getPartner() != null;

		final String key = (store.getPartner() != null && store.getPartner().getMember() != null)
				? store.getPartner().getMember().getProfileUrl()
				: null;
		final String profileUrl = (key != null && !key.isBlank()) ? s3Manager.generatePresignedUrl(key) : null;

		final String phoneNumber = (store.getPartner() != null
				&& store.getPartner().getMember() != null
				&& store.getPartner().getMember().getPhoneNum() != null)
				? store.getPartner().getMember().getPhoneNum()
				: "";

		return MapResponseDTO.StoreMapResponseDTO.builder()
				.storeId(store.getId())
				.adminName(adminName)
				.adminId(adminId)
				.name(store.getName())
				.note(content != null ? content.getNote() : null)
				.address(store.getAddress() != null ? store.getAddress() : store.getDetailAddress())
				.rate(store.getRate())
				.criterionType(content != null ? content.getCriterionType() : null)
				.optionType(content != null ? content.getOptionType() : null)
				.people(content != null ? content.getPeople() : null)
				.cost(content != null ? content.getCost() : null)
				.category(finalCategory)
				.discountRate(content != null ? content.getDiscount() : null)
				.hasPartner(hasPartner)
				.latitude(store.getLatitude())
				.longitude(store.getLongitude())
				.profileUrl(profileUrl)
				.phoneNumber(phoneNumber)
				.build();
	}

	private static List<String> extractGoods(PaperContent content) {
		if (content.getOptionType() == OptionType.SERVICE ) {
			return content.getGoods().stream()
				.map(Goods::getBelonging)
				.toList();
		}
		return null;
	}

	private static Integer extractPeople(PaperContent content) {
		if (content.getCriterionType() == CriterionType.HEADCOUNT) {
			return content.getPeople();
		}
		return null;
	}

	private static String buildPaperContentText(PaperContent content, List<String> goodsList, Integer peopleValue) {
		String result = "";

		boolean isGoodsSingle = goodsList != null && goodsList.size() == 1;
		boolean isGoodsMultiple = goodsList != null && goodsList.size() > 1;

		// 1. HEADCOUNT + SERVICE + 여러 개 goods
		if (content.getCriterionType() == CriterionType.HEADCOUNT &&
			content.getOptionType() == OptionType.SERVICE &&
			isGoodsMultiple) {
			result = peopleValue + "명 이상 식사 시 " + content.getCategory() + " 제공";
		}
		// 2. HEADCOUNT + SERVICE + 단일 goods
		else if (content.getCriterionType() == CriterionType.HEADCOUNT &&
			content.getOptionType() == OptionType.SERVICE &&
			isGoodsSingle) {
			result = peopleValue + "명 이상 식사 시 " + goodsList.get(0) + " 제공";
		}
		// 3. HEADCOUNT + DISCOUNT
		else if (content.getCriterionType() == CriterionType.HEADCOUNT &&
			content.getOptionType() == OptionType.DISCOUNT) {
			result = peopleValue + "명 이상 식사 시 " + content.getDiscount() + "% 할인";
		}
		// 4. PRICE + SERVICE + 여러 개 goods
		else if (content.getCriterionType() == CriterionType.PRICE &&
			content.getOptionType() == OptionType.SERVICE &&
			isGoodsMultiple) {
			result = content.getCost() + "원 이상 주문 시 " + content.getCategory() + " 제공";
		}
		// 5. PRICE + SERVICE + 단일 goods
		else if (content.getCriterionType() == CriterionType.PRICE &&
			content.getOptionType() == OptionType.SERVICE &&
			isGoodsSingle) {
			result = content.getCost() + "원 이상 주문 시 " + goodsList.get(0) + " 제공";
		}
		// 6. PRICE + DISCOUNT
		else if (content.getCriterionType() == CriterionType.PRICE &&
			content.getOptionType() == OptionType.DISCOUNT) {
			result = content.getCost() + "원 이상 주문 시 " + content.getDiscount() + "% 할인";
		}

		return result;
	}
}
