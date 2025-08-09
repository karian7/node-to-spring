package com.example.chatapp.controller;

import com.example.chatapp.dto.CreateRoomRequest;
import com.example.chatapp.dto.JoinRoomRequest;
import com.example.chatapp.dto.RoomResponse;
import com.example.chatapp.dto.UserSummaryResponse;
import com.example.chatapp.model.Room;
import com.example.chatapp.model.User;
import com.example.chatapp.repository.RoomRepository;
import com.example.chatapp.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Collections;
import java.util.List;
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
    public ResponseEntity<RoomResponse> joinRoom(@PathVariable String roomId, @RequestBody JoinRoomRequest joinRoomRequest, Principal principal) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found with id: " + roomId));

        User user = userRepository.findByEmail(principal.getName())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + principal.getName()));

        if (room.isHasPassword()) {
            if (joinRoomRequest.getPassword() == null || !passwordEncoder.matches(joinRoomRequest.getPassword(), room.getPassword())) {
                return ResponseEntity.status(401).build();
            }
        }

        if (!room.getParticipantIds().contains(user.getId())) {
            room.getParticipantIds().add(user.getId());
            roomRepository.save(room);
        }

        return ResponseEntity.ok(mapToRoomResponse(room, principal));
    }


    private RoomResponse mapToRoomResponse(Room room, Principal principal) {
        User creator = userRepository.findById(room.getCreatorId()).orElse(null);
        if (creator == null) {
            throw new RuntimeException("Creator not found for room " + room.getId());
        }
        UserSummaryResponse creatorSummary = new UserSummaryResponse(creator.getId(), creator.getName(), creator.getEmail());

        List<User> participants = (List<User>) userRepository.findAllById(room.getParticipantIds());
        List<UserSummaryResponse> participantSummaries = participants.stream()
                .map(p -> new UserSummaryResponse(p.getId(), p.getName(), p.getEmail()))
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
