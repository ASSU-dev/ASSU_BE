package com.assu.server.domain.auth.service;

import com.assu.server.domain.member.entity.Member;

public interface WithdrawalService {
    void withdrawCurrentUser(String authorization);
    void withdrawMember(Long memberId);
    void withdrawMember(Member member);
}
