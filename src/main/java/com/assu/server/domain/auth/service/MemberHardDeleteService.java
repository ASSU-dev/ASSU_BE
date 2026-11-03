package com.assu.server.domain.auth.service;

import java.time.LocalDateTime;

public interface MemberHardDeleteService {
    boolean hardDeleteMember(Long memberId, LocalDateTime deletedBefore);
}
