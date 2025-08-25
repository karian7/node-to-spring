package com.example.chatapp.repository;

import com.example.chatapp.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
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

    // 읽음 상태 관리를 위한 쿼리 메서드들

    /**
     * 특정 사용자가 읽지 않은 메시지들 조회
     */
    @Query("{ 'roomId': ?0, 'readers.userId': { $ne: ?1 }, 'isDeleted': false }")
    List<Message> findUnreadMessagesForUser(String roomId, String userId);

    /**
     * 특정 사용자의 읽지 않은 메시지 수 카운트
     */
    @Query(value = "{ 'roomId': ?0, 'readers.userId': { $ne: ?1 }, 'isDeleted': false }", count = true)
    long countUnreadMessagesForUser(String roomId, String userId);

    /**
     * 핀된 메시지들 조회
     */
    List<Message> findByRoomIdAndIsPinnedTrueOrderByPinnedAtDesc(String roomId);

    /**
     * AI 응답 메시지들 조회
     */
    List<Message> findByRoomIdAndAiTypeIsNotNullOrderByTimestampDesc(String roomId);

    /**
     * 특정 파일이 첨부된 메시지 조회
     */
    List<Message> findByFileIdOrderByTimestampDesc(String fileId);

    /**
     * 특정 사용자가 보낸 메시지들 조회
     */
    Page<Message> findBySenderIdAndIsDeletedFalseOrderByTimestampDesc(String senderId, Pageable pageable);

    /**
     * 특정 기간의 메시지들 조회
     */
    List<Message> findByRoomIdAndTimestampBetweenAndIsDeletedFalseOrderByTimestamp(
        String roomId, LocalDateTime start, LocalDateTime end);
}
