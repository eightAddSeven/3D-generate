package com.design3d.model.repository;

import com.design3d.model.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {

    Page<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId, Pageable pageable);

    List<Message> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}
