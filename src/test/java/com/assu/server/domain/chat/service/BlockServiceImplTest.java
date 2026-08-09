package com.assu.server.domain.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.assu.server.domain.chat.dto.BlockResponseDTO;
import com.assu.server.domain.chat.entity.Block;
import com.assu.server.domain.chat.repository.BlockRepository;
import com.assu.server.domain.common.enums.UserRole;
import com.assu.server.domain.member.entity.Member;
import com.assu.server.domain.member.repository.MemberRepository;
import com.assu.server.global.apiPayload.code.status.ErrorStatus;
import com.assu.server.global.exception.GeneralException;

@ExtendWith(MockitoExtension.class)
class BlockServiceImplTest {

	@InjectMocks
	private BlockServiceImpl blockService;

	@Mock
	private BlockRepository blockRepository;

	@Mock
	private MemberRepository memberRepository;

	private static final Long BLOCKER_ID = 1L;
	private static final Long BLOCKED_ID = 2L;

	private Member givenBlocker() {
		Member blocker = mock(Member.class);
		when(memberRepository.findById(BLOCKER_ID)).thenReturn(Optional.of(blocker));
		return blocker;
	}

	private Member givenBlockedAdmin(String name) {
		Member blocked = mock(Member.class);
		when(blocked.getRole()).thenReturn(UserRole.ADMIN);
		when(blocked.resolveName()).thenReturn(name);
		when(memberRepository.findById(BLOCKED_ID)).thenReturn(Optional.of(blocked));
		return blocked;
	}

	@Test
	@DisplayName("자기 자신을 차단하려고 하면 _BAD_REQUEST 예외가 발생한다")
	void blockMember_SelfBlock_ThrowsException() {
		// 1. Given & When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> blockService.blockMember(BLOCKER_ID, BLOCKER_ID));

		// 2. Then
		assertEquals(ErrorStatus._BAD_REQUEST, exception.getCode());
		verify(blockRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 차단한 상대를 다시 차단하면 저장 없이 null을 반환한다")
	void blockMember_AlreadyBlocked_ReturnsNull() {
		// 1. Given
		Member blocker = givenBlocker();
		Member blocked = mock(Member.class);
		when(memberRepository.findById(BLOCKED_ID)).thenReturn(Optional.of(blocked));
		when(blockRepository.existsByBlockerAndBlocked(blocker, blocked)).thenReturn(true);

		// 2. When
		BlockResponseDTO.BlockMemberDTO response = blockService.blockMember(BLOCKER_ID, BLOCKED_ID);

		// 3. Then
		assertNull(response);
		verify(blockRepository, never()).save(any());
	}

	@Test
	@DisplayName("관리자를 차단하면 차단 관계가 저장되고 차단된 관리자 이름이 반환된다")
	void blockMember_AdminBlocked_SavesBlock() {
		// 1. Given
		Member blocker = givenBlocker();
		Member blocked = givenBlockedAdmin("총학생회");
		when(blockRepository.existsByBlockerAndBlocked(blocker, blocked)).thenReturn(false);

		// 2. When
		BlockResponseDTO.BlockMemberDTO response = blockService.blockMember(BLOCKER_ID, BLOCKED_ID);

		// 3. Then
		assertEquals(BLOCKED_ID, response.memberId());
		assertEquals("총학생회", response.name());

		ArgumentCaptor<Block> captor = ArgumentCaptor.forClass(Block.class);
		verify(blockRepository, times(1)).save(captor.capture());
		assertEquals(blocker, captor.getValue().getBlocker());
		assertEquals(blocked, captor.getValue().getBlocked());
	}

	@Test
	@DisplayName("관리자/파트너가 아닌 회원을 차단하려고 하면 _BAD_REQUEST 예외가 발생한다")
	void blockMember_InvalidRole_ThrowsException() {
		// 1. Given (학생은 차단 대상이 될 수 없음)
		Member blocker = givenBlocker();
		Member blocked = mock(Member.class);
		when(blocked.getRole()).thenReturn(UserRole.STUDENT);
		when(memberRepository.findById(BLOCKED_ID)).thenReturn(Optional.of(blocked));
		when(blockRepository.existsByBlockerAndBlocked(blocker, blocked)).thenReturn(false);

		// 2. When
		GeneralException exception = assertThrows(GeneralException.class,
			() -> blockService.blockMember(BLOCKER_ID, BLOCKED_ID));

		// 3. Then
		assertEquals(ErrorStatus._BAD_REQUEST, exception.getCode());
		verify(blockRepository, never()).save(any());
	}

	@Test
	@DisplayName("차단 관계가 존재하면 blocked=true로 반환한다")
	void checkBlock_Blocked_ReturnsTrue() {
		// 1. Given
		Member blocker = givenBlocker();
		Member blocked = givenBlockedAdmin("총학생회");
		when(blockRepository.existsBlockRelationBetween(blocker, blocked)).thenReturn(true);

		// 2. When
		BlockResponseDTO.CheckBlockMemberDTO response = blockService.checkBlock(BLOCKER_ID, BLOCKED_ID);

		// 3. Then
		assertTrue(response.blocked());
		assertEquals("총학생회", response.name());
	}

	@Test
	@DisplayName("차단 관계가 없으면 blocked=false로 반환한다")
	void checkBlock_NotBlocked_ReturnsFalse() {
		// 1. Given
		Member blocker = givenBlocker();
		Member blocked = mock(Member.class);
		when(blocked.getRole()).thenReturn(UserRole.PARTNER);
		when(blocked.resolveName()).thenReturn("역전할머니 맥주");
		when(memberRepository.findById(BLOCKED_ID)).thenReturn(Optional.of(blocked));
		when(blockRepository.existsBlockRelationBetween(blocker, blocked)).thenReturn(false);

		// 2. When
		BlockResponseDTO.CheckBlockMemberDTO response = blockService.checkBlock(BLOCKER_ID, BLOCKED_ID);

		// 3. Then
		assertFalse(response.blocked());
		assertEquals("역전할머니 맥주", response.name());
	}

	@Test
	@DisplayName("차단 해제 시 차단 관계가 삭제되고 차단됐던 회원 정보가 반환된다")
	void unblockMember_Success_DeletesBlock() {
		// 1. Given
		Member blocker = givenBlocker();
		Member blocked = givenBlockedAdmin("총학생회");

		// 2. When
		BlockResponseDTO.BlockMemberDTO response = blockService.unblockMember(BLOCKER_ID, BLOCKED_ID);

		// 3. Then
		assertEquals(BLOCKED_ID, response.memberId());
		assertEquals("총학생회", response.name());
		verify(blockRepository, times(1)).deleteByBlockerAndBlocked(blocker, blocked);
	}

	@Test
	@DisplayName("내 차단 목록 조회 시 차단한 회원들이 리스트로 반환된다")
	void getMyBlockList_ReturnsBlockedMembers() {
		// 1. Given
		Member blocker = givenBlocker();

		Member blockedMember = mock(Member.class);
		when(blockedMember.getId()).thenReturn(BLOCKED_ID);
		when(blockedMember.resolveName()).thenReturn("역전할머니 맥주");

		Block block = Block.builder().blocker(blocker).blocked(blockedMember).build();
		when(blockRepository.findByBlocker(blocker)).thenReturn(List.of(block));

		// 2. When
		List<BlockResponseDTO.BlockMemberDTO> response = blockService.getMyBlockList(BLOCKER_ID);

		// 3. Then
		assertEquals(1, response.size());
		assertEquals(BLOCKED_ID, response.get(0).memberId());
		assertEquals("역전할머니 맥주", response.get(0).name());
	}
}
