package com.assu.server.domain.partnership.dto;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.assu.server.domain.partnership.entity.Goods;
import com.assu.server.domain.partnership.entity.Paper;
import com.assu.server.domain.partnership.entity.PaperContent;
import com.assu.server.domain.partnership.entity.enums.CriterionType;
import com.assu.server.domain.partnership.entity.enums.OptionType;

public record PaperContentResponseDTO(
	Long adminId,
	String adminName,
	String paperContent,
	Long contentId,
	List<String> goods,
	Integer people,
	Long cost,
	String partnershipPeriod
) {
	private static final DateTimeFormatter PERIOD_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy.MM.dd");

	public static List<PaperContentResponseDTO> toContentResponseList(List<PaperContent> contents) {
		return contents.stream()
			.map(PaperContentResponseDTO::toContentResponse)
			.toList();
	}


	public static PaperContentResponseDTO toContentResponse(PaperContent content) {
		List<String> goodsList = extractGoods(content);
		Integer peopleValue = extractPeople(content);

		String paperContentText;
		if(content.getNote()!= null){
			paperContentText = content.getNote();
		}else{
			paperContentText = buildPaperContentText(content, goodsList, peopleValue);
		}

		return new PaperContentResponseDTO(
			content.getPaper().getAdmin().getId(),
			content.getPaper().getAdmin().getName(),
			paperContentText,
			content.getId(),
			goodsList,
			peopleValue,
			content.getCost(),
			buildPartnershipPeriod(content.getPaper())
		);

	}


	private static String buildPartnershipPeriod(Paper paper) {
		if (paper.getPartnershipPeriodStart() == null || paper.getPartnershipPeriodEnd() == null) {
			return null;
		}
		return "제휴기간 " + paper.getPartnershipPeriodStart().format(PERIOD_DATE_FORMAT)
			+ " ~ " + paper.getPartnershipPeriodEnd().format(PERIOD_DATE_FORMAT);
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
			result = peopleValue + "명 이상 방문 시 " + content.getCategory() + " 제공";
		}
		// 2. HEADCOUNT + SERVICE + 단일 goods
		else if (content.getCriterionType() == CriterionType.HEADCOUNT &&
			content.getOptionType() == OptionType.SERVICE &&
			isGoodsSingle) {
			result = peopleValue + "명 이상 방문 시 " + goodsList.get(0) + " 제공";
		}
		// 3. HEADCOUNT + DISCOUNT
		else if (content.getCriterionType() == CriterionType.HEADCOUNT &&
			content.getOptionType() == OptionType.DISCOUNT) {
			result = peopleValue + "명 이상 방문 시 " + content.getDiscount() + "% 할인";
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