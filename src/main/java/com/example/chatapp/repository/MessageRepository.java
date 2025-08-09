package com.example.chatapp.repository;

import com.example.chatapp.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {
    List<Message> findByRoomId(String roomId);
    Page<Message> findByRoomId(String roomId, Pageable pageable);
}
