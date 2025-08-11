package com.example.chatapp.controller;

import com.example.chatapp.dto.*;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public ResponseEntity<List<RoomResponse>> getAllRooms(Principal principal) {
        List<Room> rooms = roomRepository.findAll();
        List<RoomResponse> roomResponses = rooms.stream()
                .map(room -> mapToRoomResponse(room, principal))
                .collect(Collectors.toList());
        return ResponseEntity.ok(roomResponses);
    }

    @PostMapping
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody CreateRoomRequest createRoomRequest, Principal principal) {
        User creator = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

        Room room = new Room();
        room.setName(createRoomRequest.getName());
        room.setCreatorId(creator.getId());
        room.getParticipantIds().add(creator.getId());

        if (createRoomRequest.getPassword() != null && !createRoomRequest.getPassword().isEmpty()) {
            room.setHasPassword(true);
            room.setPassword(passwordEncoder.encode(createRoomRequest.getPassword()));
        }

        Room savedRoom = roomRepository.save(room);
        return ResponseEntity.status(201).body(mapToRoomResponse(savedRoom, principal));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<RoomResponse> getRoomById(@PathVariable String roomId, Principal principal) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));
        return ResponseEntity.ok(mapToRoomResponse(room, principal));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String roomId, @RequestBody JoinRoomRequest joinRoomRequest, Principal principal) {
        try {
            Optional<Room> roomOpt = roomRepository.findById(roomId);
            if (roomOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse(false, "채팅방을 찾을 수 없습니다."));
            }

            Room room = roomOpt.get();

            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 비밀번호 보호된 방인 경우 비밀번호 확인
            if (room.isHasPassword()) {
                if (joinRoomRequest.getPassword() == null ||
                        !passwordEncoder.matches(joinRoomRequest.getPassword(), room.getPassword())) {
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(new ErrorResponse(false, "채팅방 비밀번호가 올바르지 않습니다."));
                }
            }

            // 이미 참여중인지 확인
            if (room.getParticipantIds().contains(user.getId())) {
                return ResponseEntity.ok(new AuthResponse(true, "이미 참여중인 채팅방입니다."));
            }

            // 채팅방 참여
            room.getParticipantIds().add(user.getId());
            Room savedRoom = roomRepository.save(room);

            return ResponseEntity.ok(new AuthResponse(true, "채팅방에 성공적으로 참여했습니다."));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "채팅방 참여 중 오류가 발생했습니다: " + e.getMessage()));
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

            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

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
            if (room.getCreatorId().equals(user.getId()) && !room.getParticipantIds().isEmpty()) {
                String newCreatorId = room.getParticipantIds().iterator().next();
                room.setCreatorId(newCreatorId);
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

            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 방장인지 확인
            if (!room.getCreatorId().equals(user.getId())) {
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
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

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
            User user = userRepository.findByEmail(principal.getName())
                    .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

            // 사용자가 생성한 채팅방 조회
            List<Room> createdRooms = roomRepository.findByCreatorId(user.getId());

            List<RoomResponse> roomResponses = createdRooms.stream()
                    .map(room -> mapToRoomResponse(room, principal))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(roomResponses);

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, "생성한 채팅방 목록 조회 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    private RoomResponse mapToRoomResponse(Room room, Principal principal) {
        User creator = userRepository.findById(room.getCreatorId()).orElse(null);
        if (creator == null) {
            throw new RuntimeException("Creator not found for room " + room.getId());
        }
        UserSummaryResponse creatorSummary = new UserSummaryResponse(creator.getId(), creator.getName(), creator.getEmail(), creator.getProfileImage());

        List<User> participants = userRepository.findAllById(room.getParticipantIds());
        List<UserSummaryResponse> participantSummaries = participants.stream()
                .map(p -> new UserSummaryResponse(p.getId(), p.getName(), p.getEmail(), p.getProfileImage()))
                .collect(Collectors.toList());

        boolean isCreator = principal != null && creator.getEmail().equals(principal.getName());

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
