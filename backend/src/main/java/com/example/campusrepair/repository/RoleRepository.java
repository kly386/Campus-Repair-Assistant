package com.example.campusrepair.repository;

import com.example.campusrepair.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByCodeAndIsDeleted(String code, Integer isDeleted);

    List<Role> findByIsDeletedOrderByIdAsc(Integer isDeleted);
}