package com.example.campusrepair.repository;

import com.example.campusrepair.entity.RepairOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepairOrderRepository extends JpaRepository<RepairOrder, Long> {

    List<RepairOrder> findByStudentId(Long studentId);

    List<RepairOrder> findByStatus(String status);
}