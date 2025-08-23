package com.example.chatapp.service;

import com.example.chatapp.dto.*;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MongoTemplate mongoTemplate;

    public PagedResponse<RoomResponse> getAllRoomsWithPagination(
            com.example.chatapp.dto.PageRequest pageRequest,
            Principal principal) {

        try {
            // 정렬 설정 검증
            if (!pageRequest.isValidSortField()) {
                pageRequest.setSortField("createdAt");
            }
            if (!pageRequest.isValidSortOrder()) {
                pageRequest.setSortOrder("desc");
            }

            // 정렬 방향 설정
            Sort.Direction direction = "desc".equals(pageRequest.getSortOrder())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

            // 정렬 필드 매핑 (participantsCount는 특별 처리 필요)
            String sortField = pageRequest.getSortField();
            if ("participantsCount".equals(sortField)) {
                sortField = "participantIds"; // MongoDB 필드명으로 변경
            }

            // Pageable 객체 생성
            PageRequest springPageRequest = PageRequest.of(
                pageRequest.getPage(),
                pageRequest.getPageSize(),
                Sort.by(direction, sortField)
            );

            // 검색어가 있는 경우와 없는 경우 분리
            Page<Room> roomPage;
            if (pageRequest.getSearch() != null && !pageRequest.getSearch().trim().isEmpty()) {
                roomPage = roomRepository.findByNameContainingIgnoreCase(
                    pageRequest.getSearch().trim(), springPageRequest);
            } else {
                roomPage = roomRepository.findAll(springPageRequest);
            }

            // Room을 RoomResponse로 변환
            List<RoomResponse> roomResponses = roomPage.getContent().stream()
                .map(room -> mapToRoomResponse(room, principal))
                .collect(Collectors.toList());

            // 메타데이터 생성
            PageMetadata metadata = PageMetadata.builder()
                .total(roomPage.getTotalElements())
                .page(pageRequest.getPage())
                .pageSize(pageRequest.getPageSize())
                .totalPages(roomPage.getTotalPages())
                .hasMore(roomPage.hasNext())
                .currentCount(roomResponses.size())
                .sort(PageMetadata.SortInfo.builder()
                    .field(pageRequest.getSortField())
                    .order(pageRequest.getSortOrder())
                    .build())
                .build();

            return PagedResponse.<RoomResponse>builder()
                .success(true)
                .data(roomResponses)
                .metadata(metadata)
                .build();

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);
            return PagedResponse.<RoomResponse>builder()
                .success(false)
                .data(List.of())
                .build();
        }
    }

    public HealthResponse getHealthStatus() {
        try {
            long startTime = System.currentTimeMillis();

            // MongoDB 연결 상태 확인
            boolean isMongoConnected = false;
            long latency = 0;

            try {
                // 간단한 쿼리로 연결 상태 및 지연 시간 측정
                roomRepository.findOneForHealthCheck();
                long endTime = System.currentTimeMillis();
                latency = endTime - startTime;
                isMongoConnected = true;
            } catch (Exception e) {
                log.warn("MongoDB 연결 확인 실패", e);
                isMongoConnected = false;
            }

            // 최근 활동 조회
            LocalDateTime lastActivity = null;
            try {
                Optional<Room> recentRoom = roomRepository.findMostRecentRoom();
                if (recentRoom.isPresent()) {
                    lastActivity = recentRoom.get().getCreatedAt();
                }
            } catch (Exception e) {
                log.warn("최근 활동 조회 실패", e);
            }

            // 서비스 상태 정보 구성
            Map<String, HealthResponse.ServiceHealth> services = new HashMap<>();
            services.put("database", HealthResponse.ServiceHealth.builder()
                .connected(isMongoConnected)
                .latency(latency)
                .build());

            return HealthResponse.builder()
                .success(true)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .services(services)
                .lastActivity(lastActivity)
                .build();

        } catch (Exception e) {
            log.error("Health check 실행 중 에러 발생", e);
            return HealthResponse.builder()
                .success(false)
                .timestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .services(new HashMap<>())
                .build();
        }
    }

    public Room createRoom(CreateRoomRequest createRoomRequest, Principal principal) {
        User creator = userRepository.findById(principal.getName())
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + principal.getName()));

        Room room = new Room();
        room.setName(createRoomRequest.getName().trim());
        room.setCreator(creator.getId());
        room.getParticipantIds().add(creator.getId());

        if (createRoomRequest.getPassword() != null && !createRoomRequest.getPassword().isEmpty()) {
            room.setHasPassword(true);
            room.setPassword(passwordEncoder.encode(createRoomRequest.getPassword()));
        }

        return roomRepository.save(room);
    }

    public Optional<Room> findRoomById(String roomId) {
        return roomRepository.findById(roomId);
    }

    public boolean joinRoom(String roomId, String password, Principal principal) {
        Optional<Room> roomOpt = roomRepository.findById(roomId);
        if (roomOpt.isEmpty()) {
            return false;
        }

        Room room = roomOpt.get();
        User user = userRepository.findById(principal.getName())
            .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다: " + principal.getName()));

        // 비밀번호 확인
        if (room.isHasPassword()) {
            if (password == null || !passwordEncoder.matches(password, room.getPassword())) {
                throw new RuntimeException("비밀번호가 올바르지 않습니다.");
            }
        }

        // 이미 참여중인지 확인
        if (room.getParticipantIds().contains(user.getId())) {
            return true; // 이미 참여중
        }

        // 채팅방 참여
        room.getParticipantIds().add(user.getId());
        roomRepository.save(room);

        return true;
    }

    private RoomResponse mapToRoomResponse(Room room, Principal principal) {
        if (room == null) return null;

        User creator = null;
        if (room.getCreator() != null) {
            creator = userRepository.findById(room.getCreator()).orElse(null);
        }

        List<User> participants = userRepository.findAllById(room.getParticipantIds());

        return RoomResponse.builder()
            .id(room.getId())
            .name(room.getName() != null ? room.getName() : "제목 없음")
            .hasPassword(room.isHasPassword())
            .creator(creator != null ? UserResponse.builder()
                .id(creator.getId())
                .name(creator.getName() != null ? creator.getName() : "알 수 없음")
                .email(creator.getEmail() != null ? creator.getEmail() : "")
                .build() : null)
            .participants(participants.stream()
                .filter(p -> p != null && p.getId() != null)
                .map(p -> UserResponse.builder()
                    .id(p.getId())
                    .name(p.getName() != null ? p.getName() : "알 수 없음")
                    .email(p.getEmail() != null ? p.getEmail() : "")
                    .build())
                .collect(Collectors.toList()))
            .participantsCount(participants.size())
            .createdAt(room.getCreatedAt() != null ? room.getCreatedAt() : LocalDateTime.now())
            .isCreator(creator != null && principal != null &&
                creator.getId().equals(principal.getName()))
            .build();
    }
}
