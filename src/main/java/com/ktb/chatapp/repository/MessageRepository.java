package com.ktb.chatapp.repository;

import com.ktb.chatapp.model.Message;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    Page<Message> findByRoomIdAndIsDeletedAndTimestampBefore(String roomId, Boolean isDeleted, LocalDateTime timestamp, Pageable pageable);

    // 읽음 상태 관리를 위한 쿼리 메서드들

    /**
     * 특정 사용자가 읽지 않은 메시지들 조회 (삭제되지 않은 메시지만)
     */
    @Query("{ 'room': ?0, 'isDeleted': false, 'readers.userId': { $ne: ?1 } }")
    List<Message> findUnreadMessagesForUser(String roomId, String userId);

    /**
     * 특정 사용자의 읽지 않은 메시지 수 카운트 (삭제되지 않은 메시지만)
     */
    @Query(value = "{ 'room': ?0, 'isDeleted': false, 'readers.userId': { $ne: ?1 } }", count = true)
    long countUnreadMessagesForUser(String roomId, String userId);


    /**
     * roomId를 검증하며 읽음 처리 상태를 원자적으로 업데이트 (삭제되지 않은 메시지만)
     * @return 업데이트된 문서 수
     */
    @Query("{'_id': {$in: ?0}, 'room': ?1, 'isDeleted': false, 'readers.userId': {$ne: ?2}}")
    @Update("{$push: {'readers': ?3}}")
    long addReaderToMessages(List<String> messageIds, String roomId, String userId, Message.MessageReader reader);

    /**
     * fileId로 메시지 조회 (파일 권한 검증용)
     */
    Optional<Message> findByFileId(String fileId);
    
    void deleteByRoomId(String id);
}
