package com.ktb.chatapp.controller;

import com.ktb.chatapp.annotation.RateLimit;
import com.ktb.chatapp.dto.*;
import com.ktb.chatapp.model.Room;
import com.ktb.chatapp.model.User;
import com.ktb.chatapp.repository.UserRepository;
import com.ktb.chatapp.service.RoomService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final UserRepository userRepository;
    private final RoomService roomService;

    @Value("${spring.profiles.active:production}")
    private String activeProfile;

    // Health Check 엔드포인트
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        try {
            HealthResponse healthResponse = roomService.getHealthStatus();

            // 캐시 비활성화 헤더 설정
            return ResponseEntity
                .status(healthResponse.isSuccess() ? 200 : 503)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .body(healthResponse);

        } catch (Exception e) {
            log.error("Health check 에러", e);

            HealthResponse errorResponse = HealthResponse.builder()
                .success(false)
                .build();

            return ResponseEntity
                .status(503)
                .cacheControl(CacheControl.noCache())
                .body(errorResponse);
        }
    }

    // 페이지네이션이 적용된 채팅방 목록 조회
    @GetMapping
    @RateLimit
    public ResponseEntity<?> getAllRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) String search,
            Principal principal) {

        try {
            // PageRequest DTO 생성
            PageRequest pageRequest = new PageRequest();
            pageRequest.setPage(Math.max(0, page));
            pageRequest.setPageSize(Math.min(Math.max(1, pageSize), 50));
            pageRequest.setSortField(sortField);
            pageRequest.setSortOrder(sortOrder);
            pageRequest.setSearch(search);

            // 서비스에서 페이지네이션 처리
            PagedResponse<RoomResponse> response = roomService.getAllRoomsWithPagination(pageRequest, principal.getName());

            // 캐시 설정
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)))
                .header("Last-Modified", java.time.Instant.now().toString())
                .body(response);

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);

            // 환경별 에러 처리
            ErrorResponse errorResponse = new ErrorResponse(false, "채팅방 목록을 불러오는데 실패했습니다.");
            if ("development".equals(activeProfile)) {
                // 개발 환경에서는 상세 에러 정보 제공
                errorResponse = ErrorResponse.builder()
                    .success(false)
                    .message("채팅방 목록을 불러오는데 실패했습니다.")
                    .error(Map.of(
                        "code", "ROOMS_FETCH_ERROR",
                        "details", e.getMessage(),
                        "stack", e.getStackTrace()
                    ))
                    .build();
            }

            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest createRoomRequest, Principal principal) {
        try {
            if (createRoomRequest.getName() == null || createRoomRequest.getName().trim().isEmpty()) {
                return ResponseEntity.status(400).body(
                    ApiResponse.error("방 이름은 필수입니다.")
                );
            }

            Room savedRoom = roomService.createRoom(createRoomRequest, principal.getName());
            RoomResponse roomResponse = mapToRoomResponse(savedRoom, principal.getName());

            return ResponseEntity.status(201).body(
                Map.of(
                    "success", true,
                    "data", roomResponse
                )
            );

        } catch (Exception e) {
            log.error("방 생성 에러", e);

            String errorMessage = "채팅방 생성에 실패했습니다.";
            if ("development".equals(activeProfile)) {
                errorMessage += " (" + e.getMessage() + ")";
            }

            return ResponseEntity.status(500).body(
                ApiResponse.error(errorMessage)
            );
        }
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<?> getRoomById(@PathVariable String roomId, Principal principal) {
        try {
            Optional<Room> roomOpt = roomService.findRoomById(roomId);
            if (roomOpt.isEmpty()) {
                return ResponseEntity.status(404).body(
                    ApiResponse.error("채팅방을 찾을 수 없습니다.")
                );
            }

            Room room = roomOpt.get();
            RoomResponse roomResponse = mapToRoomResponse(room, principal.getName());

            return ResponseEntity.ok(
                Map.of(
                    "success", true,
                    "data", roomResponse
                )
            );

        } catch (Exception e) {
            log.error("채팅방 조회 에러", e);
            return ResponseEntity.status(500).body(
                ApiResponse.error("채팅방 정보를 불러오는데 실패했습니다.")
            );
        }
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody JoinRoomRequest joinRoomRequest, Principal principal) {
        try {
            Room joinedRoom = roomService.joinRoom(roomId, joinRoomRequest.getPassword(), principal.getName());

            if (joinedRoom == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("채팅방을 찾을 수 없습니다."));
            }

            RoomResponse roomResponse = mapToRoomResponse(joinedRoom, principal.getName());
            
            return ResponseEntity.ok(
                Map.of(
                    "success", true,
                    "data", roomResponse
                )
            );

        } catch (RuntimeException e) {
            if (e.getMessage().contains("비밀번호")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(ApiResponse.error("비밀번호가 일치하지 않습니다."));
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(e.getMessage()));

        } catch (Exception e) {
            log.error("채팅방 참여 에러", e);
            return ResponseEntity.status(500).body(
                ApiResponse.error("채팅방 참여에 실패했습니다.")
            );
        }
    }

    private RoomResponse mapToRoomResponse(Room room, String name) {
        User creator = userRepository.findById(room.getCreator()).orElse(null);
        if (creator == null) {
            throw new RuntimeException("Creator not found for room " + room.getId());
        }
        UserResponse creatorSummary = UserResponse.from(creator);
        List<UserResponse> participantSummaries = room.getParticipantIds()
                .stream()
                .map(userRepository::findById).peek(optUser -> {
                    if (optUser.isEmpty()) {
                        log.warn("Participant not found: roomId={}, userId={}", room.getId(), optUser);
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(UserResponse::from)
                .toList();

        boolean isCreator = room.getCreator().equals(name);

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.isHasPassword(),
                creatorSummary,
                participantSummaries,
                room.getCreatedAt() != null ? room.getCreatedAt() : LocalDateTime.now(),
                isCreator
        );
    }
}
