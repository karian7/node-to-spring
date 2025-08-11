package com.example.chatapp.repository;

import com.example.chatapp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByEmail(String email);

    // 사용자 검색 (이름으로 검색, 현재 사용자 제외)
    Page<User> findByNameContainingIgnoreCaseAndEmailNot(String name, String excludeEmail, Pageable pageable);

    // 모든 사용자 조회 (현재 사용자 제외)
    Page<User> findByEmailNot(String excludeEmail, Pageable pageable);
}
