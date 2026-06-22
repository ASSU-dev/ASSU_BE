package com.assu.server.domain.auth.service;

import com.assu.server.domain.auth.dto.login.CommonLoginRequestDTO;
import com.assu.server.domain.auth.dto.login.RefreshResponseDTO;
import com.assu.server.domain.auth.dto.common.TokensDTO;
import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.security.adapter.RealmAuthAdapter;
import com.assu.server.domain.auth.security.jwt.JwtUtil;
import com.assu.server.domain.auth.security.token.LoginUsernamePasswordAuthenticationToken;
import com.assu.server.domain.auth.dto.backoffice.BackofficeLoginResponseDTO;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeAuthServiceImpl implements BackofficeAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final List<RealmAuthAdapter> realmAuthAdapters;

    @Override
    public BackofficeLoginResponseDTO login(CommonLoginRequestDTO request) {
        Authentication authentication = authenticationManager.authenticate(
                new LoginUsernamePasswordAuthenticationToken(
                        AuthRealm.COMMON,
                        request.email(),
                        request.password()
                )
        );

        RealmAuthAdapter adapter = pickAdapter(AuthRealm.COMMON);
        Member member = adapter.loadMember(authentication.getName());

        if (member.getRole() != UserRole.BACKOFFICE) {
            throw new GeneralException(ErrorStatus.NO_BACKOFFICE_TYPE);
        }

        TokensDTO tokens = jwtUtil.issueBackofficeTokens(
                member.getId(),
                authentication.getName(),
                member.getRole(),
                adapter.authRealmValue()
        );

        return BackofficeLoginResponseDTO.from(member, tokens);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public RefreshResponseDTO refresh(String refreshToken) {
        io.jsonwebtoken.Claims refreshClaims = jwtUtil.validateTokenOnlySignature(refreshToken);
        if (!jwtUtil.isBackofficeAudience(refreshClaims)) {
            throw new GeneralException(ErrorStatus.JWT_AUDIENCE_MISMATCH);
        }

        TokensDTO rotated = jwtUtil.rotateRefreshToken(refreshToken);
        Long memberId = ((Number) jwtUtil.validateTokenOnlySignature(rotated.accessToken()).get("userId")).longValue();
        return RefreshResponseDTO.from(memberId, rotated);
    }

    private RealmAuthAdapter pickAdapter(AuthRealm realm) {
        return realmAuthAdapters.stream()
                .filter(a -> a.supports(realm))
                .findFirst()
                .orElseThrow(() -> new CustomAuthException(ErrorStatus.AUTHORIZATION_EXCEPTION));
    }
}
