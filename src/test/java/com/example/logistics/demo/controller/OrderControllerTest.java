package com.example.logistics.demo.controller;

import com.example.logistics.demo.dto.CreateOrderRequest;
import com.example.logistics.demo.entity.Courier;
import com.example.logistics.demo.entity.CourierStatus;
import com.example.logistics.demo.entity.Order;
import com.example.logistics.demo.entity.OrderStatus;
import com.example.logistics.demo.service.OrderService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @Test
    void shouldCreateOrder() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest("张三", "北京", "13800000000");
        Order order = new Order(1L, "张三", "北京", "13800000000", OrderStatus.CREATED, null, LocalDateTime.now());

        when(orderService.create(any(CreateOrderRequest.class))).thenReturn(order);

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.customerName").value("张三"))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
    }

    @Test
    void shouldGetAllOrders() throws Exception {
        Order order = new Order(1L, "张三", "北京", "13800000000", OrderStatus.CREATED, null, LocalDateTime.now());
        when(orderService.findAll()).thenReturn(List.of(order));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].customerName").value("张三"))
                .andExpect(jsonPath("$.data[0].status").value("CREATED"));
    }

    @Test
    void shouldGetOrderById() throws Exception {
        Order order = new Order(1L, "李四", "上海", "13900000000", OrderStatus.CREATED, null, LocalDateTime.now());
        when(orderService.findById(1L)).thenReturn(order);

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.customerName").value("李四"))
                .andExpect(jsonPath("$.data.phone").value("13900000000"))
                .andExpect(jsonPath("$.data.address").value("上海"));
    }

    @Test
    void shouldAssignCourier() throws Exception {
        Courier courier = new Courier(5L, "配送员1", "136", CourierStatus.BUSY, null);
        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.ASSIGNED, courier, LocalDateTime.now());
        when(orderService.assignCourier(1L, 5L)).thenReturn(order);

        mockMvc.perform(put("/orders/1/assign/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.courier.id").value(5))
                .andExpect(jsonPath("$.data.courier.name").value("配送员1"));
    }

    @Test
    void shouldCancelOrder() throws Exception {
        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.CANCELLED, null, LocalDateTime.now());
        when(orderService.cancelOrder(1L)).thenReturn(order);

        mockMvc.perform(put("/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void shouldGetOrdersByPage() throws Exception {
        Order order = new Order(1L, "张三", "北京", "138", OrderStatus.CREATED, null, LocalDateTime.now());
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderService.findPage(0, 5)).thenReturn(page);

        mockMvc.perform(get("/orders/page")
                .param("page", "0")
                .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content[0].customerName").value("张三"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void shouldReturn400WhenCreateOrderWithEmptyName() throws Exception {
        String invalidJson = """
                {"customerName": "", "address": "北京", "phone": "13800000000"}
                """;

        mockMvc.perform(post("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest());
    }
}
