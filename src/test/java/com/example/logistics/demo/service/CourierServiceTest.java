package com.example.logistics.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.logistics.demo.dto.CreateCourierRequest;
import com.example.logistics.demo.dto.UpdateCourierRequest;
import com.example.logistics.demo.entity.Courier;
import com.example.logistics.demo.entity.CourierStatus;
import com.example.logistics.demo.exception.CourierNotFoundException;
import com.example.logistics.demo.repository.CourierRepository;

@ExtendWith(MockitoExtension.class)
class CourierServiceTest {

    @Mock
    private CourierRepository courierRepository;

    @InjectMocks
    private CourierService courierService;

    // ==================== 创建配送员 ====================

    @Test
    void shouldCreateCourier() {
        // Given：前端传来姓名和手机号
        CreateCourierRequest request = new CreateCourierRequest("张三", "13800000000");

        // save 的时候给新配送员塞个 id=1（模拟数据库自增）
        when(courierRepository.save(any(Courier.class))).thenAnswer(inv -> {
            Courier c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        // When：调用 create
        Courier result = courierService.create(request);

        // Then：id 自动生成，姓名和状态正确
        assertEquals(1L, result.getId());
        assertEquals("张三", result.getName());
        assertEquals("13800000000", result.getPhone());
        assertEquals(CourierStatus.AVAILABLE, result.getStatus());
    }

    @Test
    void shouldCreateCourierWithCorrectPhone() {
        // 测一下 save 时传入的 phone 确实来自 request
        CreateCourierRequest request = new CreateCourierRequest("李四", "13999999999");

        // ArgumentCaptor：抓出传给 save() 的参数，事后检查
        ArgumentCaptor<Courier> captor = ArgumentCaptor.forClass(Courier.class);
        when(courierRepository.save(captor.capture())).thenAnswer(inv -> {
            Courier c = inv.getArgument(0);
            c.setId(2L);
            return c;
        });

        courierService.create(request);

        assertEquals("13999999999", captor.getValue().getPhone());
    }

    // ==================== 查询全部 ====================

    @Test
    void shouldFindAllCouriers() {
        // Given：数据库里有 2 个配送员
        Courier c1 = new Courier(1L, "张三", "138", CourierStatus.AVAILABLE, null);
        Courier c2 = new Courier(2L, "李四", "139", CourierStatus.BUSY, null);
        when(courierRepository.findAll()).thenReturn(List.of(c1, c2));

        // When
        List<Courier> result = courierService.findAll();

        // Then
        assertEquals(2, result.size());
        assertEquals("张三", result.get(0).getName());
        assertEquals("李四", result.get(1).getName());
    }

    // ==================== 按 ID 查询 ====================

    @Test
    void shouldFindCourierByIdWhenExists() {
        Courier courier = new Courier(1L, "张三", "138", CourierStatus.AVAILABLE, null);
        when(courierRepository.findById(1L)).thenReturn(Optional.of(courier));

        Courier result = courierService.findById(1L);

        assertEquals("张三", result.getName());
        assertEquals(CourierStatus.AVAILABLE, result.getStatus());
    }

    @Test
    void shouldThrowWhenCourierNotFound() {
        // Given：数据库里没有 id=999 的配送员
        when(courierRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then：应该抛异常，消息是"配送员不存在"
        RuntimeException ex = assertThrows(CourierNotFoundException.class,
                () -> courierService.findById(999L));
        assertEquals("配送员不存在", ex.getMessage());
    }

    // ==================== 更新配送员 ====================

    @Test
    void shouldUpdateCourier() {
        // Given：原配送员叫"张三"
        Courier existing = new Courier(1L, "张三", "138", CourierStatus.AVAILABLE, null);
        when(courierRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(courierRepository.save(any(Courier.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateCourierRequest request = new UpdateCourierRequest();
        request.setName("张三丰");
        request.setPhone("13600000000");

        // When
        Courier result = courierService.update(1L, request);

        // Then：名字和手机号都更新了
        assertEquals("张三丰", result.getName());
        assertEquals("13600000000", result.getPhone());
    }

    @Test
    void shouldThrowWhenUpdateNonExistentCourier() {
        when(courierRepository.findById(999L)).thenReturn(Optional.empty());
        UpdateCourierRequest request = new UpdateCourierRequest();

        RuntimeException ex = assertThrows(CourierNotFoundException.class,
                () -> courierService.update(999L, request));
        assertEquals("配送员不存在", ex.getMessage());

        // 配送员不存在，不应该调用 save
        verify(courierRepository, never()).save(any());
    }

    // ==================== 删除配送员 ====================

    @Test
    void shouldDeleteCourierWhenExists() {
        // Given：配送员存在
        when(courierRepository.existsById(1L)).thenReturn(true);

        // When
        courierService.deleteById(1L);

        // Then：调用了 deleteById
        verify(courierRepository).deleteById(1L);
    }

    @Test
    void shouldThrowWhenDeleteNonExistentCourier() {
        // Given：配送员不存在
        when(courierRepository.existsById(999L)).thenReturn(false);

        // When & Then
        RuntimeException ex = assertThrows(CourierNotFoundException.class,
                () -> courierService.deleteById(999L));
        assertEquals("配送员不存在", ex.getMessage());

        // 不存在时不应该调用 deleteById
        verify(courierRepository, never()).deleteById(999L);
    }
}
