package com.example.chatapp.repository;

import com.example.chatapp.model.Room;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {

    // 특정 사용자가 참여중인 채팅방 목록 조회
    List<Room> findByParticipantIdsContaining(String userId);

    // 방장으로 생성한 채팅방 목록 조회
    List<Room> findByCreatorId(String creatorId);

    // 채팅방 이름으로 검색
    List<Room> findByNameContainingIgnoreCase(String name);
}
