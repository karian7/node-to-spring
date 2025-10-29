package com.example.chatapp.controller;

import com.example.chatapp.annotation.RateLimit;
import com.example.chatapp.dto.*;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import com.example.chatapp.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/rooms")
@RateLimit(maxRequests = 60) // 클래스 레벨에 기본 Rate Limit 적용
public class RoomController {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomService roomService;

    @Value("${spring.profiles.active:production}")
    private String activeProfile;

    // Health Check 엔드포인트
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> healthCheck() {
        try {
            HealthResponse healthResponse = roomService.getHealthStatus();

            // 캐시 비활성화 헤더 설정 (Node.js와 동일)
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
    public ResponseEntity<?> getAllRooms(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder,
            @RequestParam(required = false) String search,
            Principal principal) {

        try {
            // PageRequest DTO 생성
            com.example.chatapp.dto.PageRequest pageRequest = new com.example.chatapp.dto.PageRequest();
            pageRequest.setPage(Math.max(0, page));
            pageRequest.setPageSize(Math.min(Math.max(1, pageSize), 50));
            pageRequest.setSortField(sortField);
            pageRequest.setSortOrder(sortOrder);
            pageRequest.setSearch(search);

            // 서비스에서 페이지네이션 처리
            PagedResponse<RoomResponse> response = roomService.getAllRoomsWithPagination(pageRequest, principal);

            // 캐시 설정 (Node.js와 동일)
            return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(10)))
                .header("Last-Modified", java.time.Instant.now().toString())
                .body(response);

        } catch (Exception e) {
            log.error("방 목록 조회 에러", e);

            // 환경별 에러 처리 (Node.js와 동일)
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
    @RateLimit(maxRequests = 10, windowSeconds = 60) // 방 생성은 더 엄격한 제한
    public ResponseEntity<?> createRoom(@Valid @RequestBody CreateRoomRequest createRoomRequest, Principal principal) {
        try {
            if (createRoomRequest.getName() == null || createRoomRequest.getName().trim().isEmpty()) {
                return ResponseEntity.status(400).body(
                    ApiResponse.error("방 이름은 필수입니다.")
                );
            }

            Room savedRoom = roomService.createRoom(createRoomRequest, principal);
            RoomResponse roomResponse = mapToRoomResponse(savedRoom, principal);

            // TODO: WebSocket을 통한 실시간 알림 (추후 구현)
            // socketService.notifyRoomCreated(roomResponse);

            return ResponseEntity.status(201).body(
                ApiResponse.success("채팅방이 성공적으로 생성되었습니다.", roomResponse)
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
            RoomResponse roomResponse = mapToRoomResponse(room, principal);

            return ResponseEntity.ok(
                ApiResponse.success("채팅방 정보를 성공적으로 조회했습니다.", roomResponse)
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
            boolean joinResult = roomService.joinRoom(roomId, joinRoomRequest.getPassword(), principal);

            if (!joinResult) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error("채팅방을 찾을 수 없습니다."));
            }

            // TODO: WebSocket을 통한 실시간 알림 (추후 구현)
            // socketService.notifyRoomJoined(roomId, principal.getName());

            return ResponseEntity.ok(ApiResponse.success("채팅방에 성공적으로 참여했습니다."));

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

    @PostMapping("/{roomId}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String roomId, Principal principal) {
        try {
            Optional<Room> roomOpt = roomRepository.findById(roomId);
            if (roomOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(false, "채팅방을 찾을 수 없습니다."));
            }

            Room room = roomOpt.get();

            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + principal.getName()));

            // 참여중이 아닌 경우
            if (!room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(new ErrorResponse(false, "참여하지 않은 채팅방입니다."));
            }

            // 채팅방 탈퇴
            room.getParticipantIds().remove(user.getId());

            // 참여자가 없으면 채팅방 삭제
            if (room.getParticipantIds().isEmpty()) {
                roomRepository.delete(room);
                return ResponseEntity.ok(new AuthResponse(true, "채팅방을 탈퇴했습니다. 참여자가 없어 채팅방이 삭제되었습니다."));
            }

            // 방장이 나가는 경우 다른 참여자를 방장으로 변경
            if (room.getCreator().equals(user.getId()) && !room.getParticipantIds().isEmpty()) {
                String newCreatorId = room.getParticipantIds().iterator().next();
                room.setCreator(newCreatorId);
            }

            roomRepository.save(room);
            return ResponseEntity.ok(new AuthResponse(true, "채팅방을 탈퇴했습니다."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "채팅방 탈퇴 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{roomId}")
    public ResponseEntity<?> deleteRoom(@PathVariable String roomId, Principal principal) {
        try {
            Optional<Room> roomOpt = roomRepository.findById(roomId);
            if (roomOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(false, "채팅방을 찾을 수 없습니다."));
            }

            Room room = roomOpt.get();

            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + principal.getName()));

            // 방장인지 확인
            if (!room.getCreator().equals(user.getId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse(false, "채팅방을 삭제할 권한이 없습니다."));
            }

            roomRepository.delete(room);
            return ResponseEntity.ok(new AuthResponse(true, "채팅방이 삭제되었습니다."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "채팅방 삭제 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/my-rooms")
    public ResponseEntity<?> getMyRooms(Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + principal.getName()));

            // 사용자가 참여중인 채팅방 조회
            List<Room> myRooms = roomRepository.findByParticipantIdsContaining(user.getId());

            List<RoomResponse> roomResponses = myRooms.stream()
                    .map(room -> mapToRoomResponse(room, principal))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(roomResponses);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "참여 채팅방 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchRooms(@RequestParam String query, Principal principal) {
        try {
            // 채팅방 이름으로 검색
            List<Room> searchResults = roomRepository.findByNameContainingIgnoreCase(query);

            List<RoomResponse> roomResponses = searchResults.stream()
                    .map(room -> mapToRoomResponse(room, principal))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(roomResponses);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "채팅방 검색 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    @GetMapping("/created-by-me")
    public ResponseEntity<?> getMyCreatedRooms(Principal principal) {
        try {
            User user = userRepository.findById(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + principal.getName()));

            // 사용자가 생성한 채팅방 조회
            List<Room> createdRooms = roomRepository.findByCreator(user.getId());

            List<RoomResponse> roomResponses = createdRooms.stream()
                    .filter(room -> !room.getParticipantIds().contains(user.getId()))
                    .map(room -> mapToRoomResponse(room, principal))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(roomResponses);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "생성한 채팅방 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    private RoomResponse mapToRoomResponse(Room room, Principal principal) {
        User creator = userRepository.findById(room.getCreator()).orElse(null);
        if (creator == null) {
            throw new RuntimeException("Creator not found for room " + room.getId());
        }
        UserResponse creatorSummary = UserResponse.from(creator);

        List<User> participants = userRepository.findAllById(room.getParticipantIds());
        List<UserResponse> participantSummaries = participants.stream()
                .map(UserResponse::from)
                .collect(Collectors.toList());

        boolean isCreator = principal != null && room.getCreator().equals(principal.getName());

        return new RoomResponse(
                room.getId(),
                room.getName(),
                room.isHasPassword(),
                creatorSummary,
                participantSummaries,
                participantSummaries.size(),
                room.getCreatedAt(),
                isCreator
        );
    }
}
