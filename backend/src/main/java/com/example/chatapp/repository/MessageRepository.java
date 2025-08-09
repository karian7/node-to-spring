package com.example.chatapp.repository;

import com.example.chatapp.model.Message;
import com.example.chatapp.model.Room;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByRoom(Room room);
    Page<Message> findByRoom(Room room, Pageable pageable);
}
