package com.assu.server.domain.backoffice.service;

import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeOperatorResponseDTO;
import com.assu.server.domain.backoffice.entity.BackofficeUser;
import com.assu.server.domain.backoffice.repository.BackofficeUserRepository;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeOperatorServiceImpl implements BackofficeOperatorService {

    private final MemberRepository memberRepository;
    private final BackofficeUserRepository backofficeUserRepository;
    private final CommonAuthRepository commonAuthRepository;
    private final List<RealmAuthAdapter> realmAuthAdapters;

    @Override
    public BackofficeOperatorResponseDTO createOperator(BackofficeOperatorCreateRequestDTO request) {
        if (commonAuthRepository.existsByEmail(request.email())) {
            throw new CustomAuthException(ErrorStatus.EXISTED_EMAIL);
        }

        Member member = memberRepository.save(
                Member.builder()
                        .isLocationTermAgreed(true)
                        .isMarketingTermAgreed(false)
                        .role(UserRole.BACKOFFICE)
                        .isActivated(ActivationStatus.ACTIVE)
                        .build()
        );

        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        adapter.registerCredentials(member, request.email(), request.password());

        BackofficeUser backofficeUser = backofficeUserRepository.save(
                BackofficeUser.builder()
                        .member(member)
                        .name(request.name())
                        .build()
        );
        member.setProfile(backofficeUser);

        return BackofficeOperatorResponseDTO.from(member, backofficeUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BackofficeOperatorResponseDTO> listOperators() {
        return backofficeUserRepository.findAll().stream()
                .map(user -> BackofficeOperatorResponseDTO.from(user.getMember(), user))
                .toList();
    }

    private RealmAuthAdapter pickAdapter(AuthRealm realm) {
        return realmAuthAdapters.stream()
                .filter(a -> a.supports(realm))
                .findFirst()
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION));
    }
}
