package com.design3d.model.repository;

import com.design3d.model.entity.ModelFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelFileRepository extends JpaRepository<ModelFile, String> {
}
