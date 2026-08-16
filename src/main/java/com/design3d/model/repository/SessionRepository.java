package com.design3d.model.repository;

import com.design3d.model.entity.Session;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SessionRepository extends JpaRepository<Session, String> {

    Page<Session> findAllByOrderByUpdatedAtDesc(Pageable pageable);
}
