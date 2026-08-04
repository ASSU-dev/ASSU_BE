package com.assu.server.domain.backoffice.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.assu.server.domain.admin.entity.Admin;
import com.assu.server.domain.admin.repository.AdminRepository;
import com.assu.server.domain.auth.entity.CommonAuth;
import com.assu.server.domain.auth.exception.CustomAuthException;
import com.assu.server.domain.auth.repository.CommonAuthRepository;
import com.assu.server.domain.backoffice.dto.BackofficeAdminCreateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminCreateResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminFetchResponseDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminUpdateRequestDTO;
import com.assu.server.domain.backoffice.dto.BackofficeAdminUpdateResponseDTO;
import com.assu.server.domain.common.enums.ActivationStatus;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.domain.partner.repository.PartnerRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BackofficeAdminServiceImpl implements BackofficeAdminService {

	private final AdminRepository adminRepository;
	private final MemberRepository memberRepository;
	private final CommonAuthRepository commonAuthRepository;
	private final PartnerRepository partnerRepository;
	private final PasswordEncoder passwordEncoder;
	private final GeometryFactory geometryFactory;

	@Override
	@Transactional(readOnly = true)
	public BackofficeAdminFetchResponseDTO fetchAdmin() {
		List<Admin> admins = adminRepository.findAll();
		List<BackofficeAdminFetchResponseDTO.BackofficeAdminInfoDTO> adminInfoList = admins.stream()
			.map(BackofficeAdminFetchResponseDTO.BackofficeAdminInfoDTO::from)
			.collect(Collectors.toList());
		return new BackofficeAdminFetchResponseDTO(adminInfoList);
	}

	@Override
	public BackofficeAdminCreateResponseDTO createAdmin(BackofficeAdminCreateRequestDTO req) {
		// 1) 이메일 중복 체크
		if (commonAuthRepository.existsByEmail(req.email())) {
			throw new CustomAuthException(ErrorStatus.EXISTED_EMAIL);
		}

		// 2) 휴대폰 번호 중복 체크 (전화번호가 기입된 경우만)
		if (req.phoneNumber() != null && !req.phoneNumber().isBlank()) {
			if (partnerRepository.existsByPhoneNum(req.phoneNumber())
				|| adminRepository.existsByPhoneNum(req.phoneNumber())) {
				throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
			}
		}

		// 3) Member 엔티티 생성 및 저장
		Member member = memberRepository.save(
			Member.builder()
				.isLocationTermAgreed(true)
				.isMarketingTermAgreed(true)
				.role(UserRole.ADMIN)
				.isActivated(ActivationStatus.ACTIVE)
				.build()
		);

		// 4) CommonAuth 생성 및 비밀번호 암호화 저장
		String hashed = passwordEncoder.encode(req.password());
		CommonAuth commonAuth = CommonAuth.builder()
			.member(member)
			.email(req.email())
			.hashedPassword(hashed)
			.lastLoginAt(LocalDateTime.now())
			.build();
		commonAuthRepository.save(commonAuth);
		member.setCommonAuth(commonAuth);

		// 5) officeAddress 기본값 처리
		String address = req.officeAddress();
		if (address == null || address.isBlank()) {
			address = "미지정";
		}

		// 6) 위도/경도 데이터 및 Point 객체 생성
		Double lat = req.latitude() != null ? req.latitude() : 0.0;
		Double lng = req.longitude() != null ? req.longitude() : 0.0;
		Point point = toPoint(lat, lng);

		// 7) Admin 프로필 생성 및 저장
		Admin admin = adminRepository.save(
			Admin.builder()
				.major(req.major())
				.department(req.department())
				.university(req.university())
				.member(member)
				.name(req.name())
				.phoneNum(req.phoneNumber())
				.isPhoneVerified(req.phoneNumber() != null && !req.phoneNumber().isBlank())
				.officeAddress(address)
				.detailAddress(req.detailAddress())
				.signImageUrl(null) // 인감 이미지 미필수
				.point(point)
				.latitude(lat)
				.longitude(lng)
				.build()
		);
		member.setProfile(admin);

		return new BackofficeAdminCreateResponseDTO(
			member.getId(),
			commonAuth.getEmail(),
			admin.getName(),
			member.getCreatedAt()
		);
	}

	@Override
	public BackofficeAdminUpdateResponseDTO updateAdmin(Long adminId, BackofficeAdminUpdateRequestDTO req) {
		Admin admin = adminRepository.findById(adminId)
			.orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
		Member member = admin.getMember();
		CommonAuth commonAuth = member.getCommonAuth();

		// 1) 이메일 변경 처리
		if (req.email() != null && !req.email().isBlank() && !req.email().equals(commonAuth.getEmail())) {
			if (commonAuthRepository.existsByEmail(req.email())) {
				throw new CustomAuthException(ErrorStatus.EXISTED_EMAIL);
			}
			commonAuth.setEmail(req.email());
		}

		// 2) 비밀번호 변경 처리
		if (req.password() != null && !req.password().isBlank()) {
			String hashed = passwordEncoder.encode(req.password());
			commonAuth.setHashedPassword(hashed);
		}

		// 3) 휴대폰 번호 변경 처리
		if (req.phoneNumber() != null && !req.phoneNumber().equals(admin.getPhoneNum())) {
			if (!req.phoneNumber().isBlank()) {
				if (partnerRepository.existsByPhoneNum(req.phoneNumber())
					|| adminRepository.existsByPhoneNum(req.phoneNumber())) {
					throw new CustomAuthException(ErrorStatus.EXISTED_PHONE);
				}
				admin.setPhoneNum(req.phoneNumber());
				admin.setIsPhoneVerified(true);
			} else {
				// 빈값으로 수정 시 null 및 미인증 처리
				admin.setPhoneNum(null);
				admin.setIsPhoneVerified(false);
			}
		}

		// 4) 기타 프로필 정보 변경 처리
		if (req.name() != null && !req.name().isBlank()) {
			admin.setName(req.name());
		}
		if (req.university() != null) {
			admin.setUniversity(req.university());
		}
		if (req.department() != null) {
			admin.setDepartment(req.department());
		}
		if (req.major() != null) {
			admin.setMajor(req.major());
		}
		if (req.officeAddress() != null) {
			admin.setOfficeAddress(req.officeAddress());
		}
		if (req.detailAddress() != null) {
			admin.setDetailAddress(req.detailAddress());
		}

		// 5) 위도/경도 변경 처리
		if (req.latitude() != null || req.longitude() != null) {
			Double lat = req.latitude() != null ? req.latitude() : admin.getLatitude();
			Double lng = req.longitude() != null ? req.longitude() : admin.getLongitude();
			admin.setLatitude(lat);
			admin.setLongitude(lng);
			admin.setPoint(toPoint(lat, lng));
		}

		commonAuthRepository.save(commonAuth);
		adminRepository.save(admin);

		return new BackofficeAdminUpdateResponseDTO(
			admin.getId(),
			commonAuth.getEmail(),
			admin.getName(),
			admin.getPhoneNum(),
			admin.getUniversity(),
			admin.getDepartment(),
			admin.getMajor(),
			admin.getOfficeAddress(),
			admin.getDetailAddress(),
			LocalDateTime.now()
		);
	}

	@Override
	public void deleteAdmin(Long adminId) {
		Member member = memberRepository.findById(adminId)
			.orElseThrow(() -> new CustomAuthException(ErrorStatus.NO_SUCH_MEMBER));
		memberRepository.delete(member);
	}

	private Point toPoint(Double lat, Double lng) {
		if (lat == null || lng == null) {
			return null;
		}
		Point p = geometryFactory.createPoint(new Coordinate(lng, lat));
		p.setSRID(4326);
		return p;
	}
}

