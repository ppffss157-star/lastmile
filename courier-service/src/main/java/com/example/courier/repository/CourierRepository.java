package com.example.courier.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.courier.entity.Courier;
import com.example.courier.entity.CourierStatus;

public interface CourierRepository extends JpaRepository<Courier, Long> {

    List<Courier> findByStatus(CourierStatus status);
}
