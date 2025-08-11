package com.example.chatapp.repository;

import com.example.chatapp.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    Page<Message> findByRoomId(String roomId, Pageable pageable);
    Page<Message> findByRoomIdAndTimestampBefore(String roomId, LocalDateTime timestamp, Pageable pageable);

    // 배치 로딩을 위한 정렬된 조회 메서드 추가
    Page<Message> findByRoomIdOrderByTimestampDesc(String roomId, Pageable pageable);
    Page<Message> findByRoomIdAndTimestampBeforeOrderByTimestampDesc(String roomId, LocalDateTime timestamp, Pageable pageable);

    // 메시지 검색 (삭제되지 않은 메시지만)
    Page<Message> findByRoomIdAndContentContainingIgnoreCaseAndIsDeletedFalse(String roomId, String content, Pageable pageable);

    // 읽음 상태 관리를 위한 커스텀 쿼리 메서드들

    /**
     * 특정 메시지들을 특정 사용자가 읽음 상태로 업데이트
     */
    @Query("{ '_id': { $in: ?0 }, 'readers.userId': { $ne: ?1 } }")
    @Update("{ $push: { 'readers': { 'userId': ?1, 'readAt': ?2 } } }")
    void markMessagesAsReadByUser(List<String> messageIds, String userId, LocalDateTime readAt);

    /**
     * 특정 방의 모든 메시지를 특정 사용자가 읽음 상태로 업데이트
     */
    @Query("{ 'roomId': ?0, 'readers.userId': { $ne: ?1 } }")
    @Update("{ $push: { 'readers': { 'userId': ?1, 'readAt': ?2 } } }")
    void markAllMessagesInRoomAsReadByUser(String roomId, String userId, LocalDateTime readAt);

    /**
     * 특정 방에서 특정 사용자가 읽지 않은 메시지 수 조회
     */
    @Query(value = "{ 'roomId': ?0, 'readers.userId': { $ne: ?1 }, 'isDeleted': false }", count = true)
    long countUnreadMessagesInRoom(String roomId, String userId);

    /**
     * 특정 사용자가 읽지 않은 메시지들 조회
     */
    @Query("{ 'roomId': ?0, 'readers.userId': { $ne: ?1 }, 'isDeleted': false }")
    List<Message> findUnreadMessagesInRoom(String roomId, String userId);

    /**
     * 특정 시간 이후의 메시지 중 읽지 않은 메시지 수 조회
     */
    @Query(value = "{ 'roomId': ?0, 'timestamp': { $gt: ?1 }, 'readers.userId': { $ne: ?2 }, 'isDeleted': false }", count = true)
    long countUnreadMessagesSince(String roomId, LocalDateTime since, String userId);
}
