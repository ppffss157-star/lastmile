package com.example.logistics.demo.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.logistics.demo.entity.Courier;

public interface CourierRepository extends JpaRepository<Courier, Long> {
}