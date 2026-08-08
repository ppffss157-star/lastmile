package com.example.logistics.lastmile.service;

import com.example.logistics.lastmile.AbstractIntegrationTest;
import com.example.logistics.lastmile.dto.CreateCourierRequest;
import com.example.logistics.lastmile.dto.UpdateCourierRequest;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.exception.CourierNotFoundException;
import com.example.logistics.lastmile.repository.CourierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CourierService 集成测试 — 真实 MySQL。
 */
class CourierServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CourierService courierService;

    @Autowired
    private CourierRepository courierRepository;

    @BeforeEach
    void setUp() {
        courierRepository.deleteAll();
    }

    // ==================== 创建配送员 ====================

    @Test
    void shouldCreateCourier() {
        CreateCourierRequest request = new CreateCourierRequest("张三", "13800000000");
        Courier saved = courierService.create(request);

        assertNotNull(saved.getId());
        assertEquals("张三", saved.getName());
        assertEquals("13800000000", saved.getPhone());
        assertEquals(CourierStatus.AVAILABLE, saved.getStatus());

        // 验证真实存进了 MySQL
        Courier fromDb = courierRepository.findById(saved.getId()).orElseThrow();
        assertEquals("张三", fromDb.getName());
    }

    // ==================== 查询配送员 ====================

    @Test
    void shouldFindAllCouriers() {
        courierService.create(new CreateCourierRequest("张三", "13800000000"));
        courierService.create(new CreateCourierRequest("李四", "13900000000"));

        List<Courier> couriers = courierService.findAll();
        assertEquals(2, couriers.size());
    }

    @Test
    void shouldFindById() {
        Courier saved = courierService.create(new CreateCourierRequest("张三", "13800000000"));

        Courier found = courierService.findById(saved.getId());
        assertEquals("张三", found.getName());
    }

    @Test
    void shouldThrowWhenCourierNotFound() {
        assertThrows(CourierNotFoundException.class, () -> courierService.findById(99999L));
    }

    // ==================== 部分更新 ====================

    @Test
    void shouldUpdateCourier() {
        Courier saved = courierService.create(new CreateCourierRequest("张三", "13800000000"));

        UpdateCourierRequest request = new UpdateCourierRequest();
        request.setName("张三丰");
        request.setPhone("13700000000");

        Courier updated = courierService.update(saved.getId(), request);
        assertEquals("张三丰", updated.getName());
        assertEquals("13700000000", updated.getPhone());
    }

    @Test
    void shouldUpdateOnlyNameWhenPhoneNotProvided() {
        Courier saved = courierService.create(new CreateCourierRequest("张三", "13800000000"));

        UpdateCourierRequest request = new UpdateCourierRequest();
        request.setName("张三丰");

        Courier updated = courierService.update(saved.getId(), request);
        assertEquals("张三丰", updated.getName());
        assertEquals("13800000000", updated.getPhone()); // 没传的不动
    }

    @Test
    void shouldUpdateOnlyPhoneWhenNameNotProvided() {
        Courier saved = courierService.create(new CreateCourierRequest("张三", "13800000000"));

        UpdateCourierRequest request = new UpdateCourierRequest();
        request.setPhone("13700000000");

        Courier updated = courierService.update(saved.getId(), request);
        assertEquals("张三", updated.getName()); // 没传的不动
        assertEquals("13700000000", updated.getPhone());
    }

    // ==================== 删除配送员 ====================

    @Test
    void shouldDeleteCourier() {
        Courier saved = courierService.create(new CreateCourierRequest("张三", "13800000000"));
        Long id = saved.getId();

        courierService.deleteById(id);
        assertFalse(courierRepository.existsById(id));
    }

    @Test
    void shouldThrowWhenDeleteNonExistentCourier() {
        assertThrows(CourierNotFoundException.class, () -> courierService.deleteById(99999L));
    }
}
