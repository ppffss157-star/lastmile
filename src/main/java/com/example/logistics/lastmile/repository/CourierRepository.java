package com.example.logistics.lastmile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.logistics.lastmile.entity.Courier;

public interface CourierRepository extends JpaRepository<Courier, Long> {
}