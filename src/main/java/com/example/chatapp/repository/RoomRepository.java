package com.example.chatapp.repository;

import com.example.chatapp.model.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends MongoRepository<Room, String> {

    // 특정 사용자가 참여중인 채팅방 목록 조회
    List<Room> findByParticipantIdsContaining(String userId);

    // 방장으로 생성한 채팅방 목록 조회
    List<Room> findByCreator(String creator);

    // 채팅방 이름으로 검색
    List<Room> findByNameContainingIgnoreCase(String name);

    // 페이지네이션과 함께 모든 방 조회
    Page<Room> findAll(Pageable pageable);

    // 검색어와 함께 페이지네이션 조회
    Page<Room> findByNameContainingIgnoreCase(String name, Pageable pageable);

    // 가장 최근에 생성된 방 조회 (Health Check용)
    @Query(value = "{}", sort = "{ 'createdAt': -1 }")
    Optional<Room> findMostRecentRoom();

    // Health Check용 단순 조회 (지연 시간 측정)
    @Query(value = "{}", fields = "{ '_id': 1 }")
    Optional<Room> findOneForHealthCheck();
}
