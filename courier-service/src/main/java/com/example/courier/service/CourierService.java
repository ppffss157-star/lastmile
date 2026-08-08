package com.example.courier.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.courier.entity.Courier;
import com.example.courier.entity.CourierStatus;
import com.example.courier.repository.CourierRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepository courierRepository;

    @Transactional(rollbackFor = Exception.class)
    public Courier create(String name, String phone) {
        Courier courier = new Courier();
        courier.setName(name);
        courier.setPhone(phone);
        courier.setStatus(CourierStatus.AVAILABLE);
        return courierRepository.save(courier);
    }

    @Transactional(readOnly = true)
    public List<Courier> findAll() {
        return courierRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Courier> findAvailable() {
        return courierRepository.findByStatus(CourierStatus.AVAILABLE);
    }

    @Transactional(readOnly = true)
    public Courier findById(Long id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("配送员不存在: " + id));
    }

    /**
     * 派单——由 order-service 通过 REST API 调用。
     *
     * 和单体版的关键区别：
     * 单体里这段代码在 OrderService.assignCourier() 里，一个 @Transactional 包住
     * orders 和 couriers 两张表。微服务里 courier 的更新是独立事务，order-service
     * 调完这个接口后再更新自己的 orders 表。两个操作不在同一事务里。
     */
    @Transactional(rollbackFor = Exception.class)
    public Courier assignToOrder(Long courierId, Long orderId) {
        Courier courier = findById(courierId);

        if (courier.getStatus() != CourierStatus.AVAILABLE) {
            throw new RuntimeException("配送员 " + courierId + " 当前不可用，状态: " + courier.getStatus());
        }

        courier.setStatus(CourierStatus.BUSY);
        Courier saved = courierRepository.save(courier);
        log.info("[courier-service] 配送员 {} 已分配给订单 {}", courierId, orderId);
        return saved;
    }

    @Transactional(rollbackFor = Exception.class)
    public Courier release(Long courierId) {
        Courier courier = findById(courierId);
        courier.setStatus(CourierStatus.AVAILABLE);
        return courierRepository.save(courier);
    }
}
