package com.example.logistics.lastmile.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.logistics.lastmile.entity.TransactionDemoEntity;

public interface TransactionDemoRepository extends JpaRepository<TransactionDemoEntity, Long> {
}
