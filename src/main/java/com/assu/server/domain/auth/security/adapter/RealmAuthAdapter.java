package com.assu.server.domain.auth.security.adapter;

import com.assu.server.domain.auth.entity.enums.AuthRealm;
import com.assu.server.domain.member.entity.Member;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

public interface RealmAuthAdapter {
    boolean supports(AuthRealm realm);
    UserDetails loadUserDetails(String identifier);
    Member loadMember(String identifier);
    void registerCredentials(Member member, String username, String rawPassword);
    PasswordEncoder passwordEncoder();
    String authRealmValue(); // "COMMON" or "SSU"
}