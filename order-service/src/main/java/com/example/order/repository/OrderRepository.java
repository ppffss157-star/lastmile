package com.example.order.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByCourierId(Long courierId);
}
