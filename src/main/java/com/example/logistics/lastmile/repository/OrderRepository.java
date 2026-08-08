package com.example.logistics.lastmile.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.logistics.lastmile.entity.Order;
import com.example.logistics.lastmile.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT o FROM Order o WHERE o.courier.id = :courierId")
    List<Order> findByCourierId(@Param("courierId") Long courierId);

    @Query("SELECT o FROM Order o WHERE o.courier.id = :courierId")
    Page<Order> findByCourierId(@Param("courierId") Long courierId, Pageable pageable);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime dateTime);

    @Query("SELECT o.id FROM Order o")
    List<Long> findAllIds();

    @Query("SELECT o.status, COUNT(o) FROM Order o GROUP BY o.status")
    List<Object[]> countByStatus();

    @Query("SELECT DISTINCT o.customerName FROM Order o ORDER BY o.customerName")
    List<String> findDistinctCustomerNames();
}