package com.assu.server.global.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PresenceTracker {
    private final Map<Long, Set<Long>> roomSubscribers = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionToMember = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> sessionToRooms = new ConcurrentHashMap<>();
    // "sessionId:subscriptionId" -> roomId: 특정 구독 해제 시 어느 방인지 추적
    private final Map<String, Long> subToRoom = new ConcurrentHashMap<>();

    private Long parseRoomId(String dest) { // "/sub/chat/26" -> 26
        if (dest == null) return null;
        String[] p = dest.split("/");
        if (p.length == 4 && "chat".equals(p[2])) return Long.valueOf(p[3]);
        return null;
    }

    private Long memberIdFrom(Principal user) {
        if (user == null) return null;
        return Long.valueOf(user.getName());
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent e) {
        var acc = StompHeaderAccessor.wrap(e.getMessage());
        Long roomId = parseRoomId(acc.getDestination());
        Principal user = e.getUser() != null ? e.getUser() : acc.getUser();
        Long memberId = memberIdFrom(user);
        log.info("[PresenceTracker] onSubscribe dest={} eUser={} accUser={} -> memberId={} roomId={}",
                acc.getDestination(), e.getUser(), acc.getUser(), memberId, roomId);
        if (roomId == null || memberId == null) return;

        String sessionId = acc.getSessionId();
        String subId = acc.getSubscriptionId();

        sessionToMember.put(sessionId, memberId);
        sessionToRooms.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(roomId);
        roomSubscribers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(memberId);
        if (subId != null) {
            subToRoom.put(sessionId + ":" + subId, roomId);
        }

        log.debug("SUB: member {} -> room {}", memberId, roomId);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent e) {
        var acc = StompHeaderAccessor.wrap(e.getMessage());
        String sessionId = acc.getSessionId();
        String subId = acc.getSubscriptionId();

        Long roomId = subToRoom.remove(sessionId + ":" + subId);
        if (roomId == null) return; // 채팅방 구독이 아닌 경우 무시

        Long memberId = sessionToMember.get(sessionId);
        if (memberId != null) {
            var set = roomSubscribers.get(roomId);
            if (set != null) {
                set.remove(memberId);
                if (set.isEmpty()) roomSubscribers.remove(roomId);
            }
        }
        sessionToRooms.getOrDefault(sessionId, Set.of()).remove(roomId);

        log.debug("UNSUB: member {} <- room {}", memberId, roomId);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent e) {
        String sessionId = e.getSessionId();
        Long memberId = sessionToMember.remove(sessionId);
        var rooms = sessionToRooms.remove(sessionId);
        if (memberId != null && rooms != null) {
            for (Long roomId : rooms) {
                var set = roomSubscribers.get(roomId);
                if (set != null) {
                    set.remove(memberId);
                    if (set.isEmpty()) roomSubscribers.remove(roomId);
                }
            }
        }
        subToRoom.keySet().removeIf(key -> key.startsWith(sessionId + ":"));
        log.debug("DISCONNECT: session {}", sessionId);
    }

    public boolean isInRoom(Long memberId, Long roomId) {
        return roomSubscribers.getOrDefault(roomId, Set.of()).contains(memberId);
    }
}