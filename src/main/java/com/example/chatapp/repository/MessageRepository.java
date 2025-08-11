package com.example.chatapp.repository;

import com.example.chatapp.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    Page<Message> findByRoomId(String roomId, Pageable pageable);
    Page<Message> findByRoomIdAndTimestampBefore(String roomId, LocalDateTime timestamp, Pageable pageable);

    // 메시지 검색 (삭제되지 않은 메시지만)
    Page<Message> findByRoomIdAndContentContainingIgnoreCaseAndIsDeletedFalse(String roomId, String content, Pageable pageable);
}
