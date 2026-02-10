package com.assu.server.domain.appreview.service;

import com.assu.server.domain.appreview.dto.AppReviewRequestDTO;
import com.assu.server.domain.appreview.entity.AppReview;
import com.assu.server.domain.appreview.repository.AppReviewRepository;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.DatabaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class AppReviewServiceImpl implements AppReviewService {

    private final AppReviewRepository appReviewRepository;
    private final MemberRepository memberRepository;

    @Override
    public void create(AppReviewRequestDTO request, Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new DatabaseException(ErrorStatus.NO_SUCH_MEMBER));

        AppReview appReview = AppReview.builder()
                .member(member)
                .rate(request.rate())
                .content(request.content())
                .build();

        appReviewRepository.save(appReview);
    }
}
