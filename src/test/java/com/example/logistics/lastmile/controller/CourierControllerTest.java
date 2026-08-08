package com.example.logistics.lastmile.controller;

import com.example.logistics.lastmile.dto.CreateCourierRequest;
import com.example.logistics.lastmile.dto.UpdateCourierRequest;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.service.CourierService;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourierController.class)
@AutoConfigureMockMvc(addFilters = false)
class CourierControllerTest {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CourierService courierService;

    /** @WebMvcTest 不加载 Redis，Mock 给 RateLimitInterceptor 用的 */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void shouldCreateCourier() throws Exception {
        CreateCourierRequest request = new CreateCourierRequest("张三", "13800000000");
        Courier courier = new Courier(1L, "张三", "13800000000", CourierStatus.AVAILABLE, null);

        when(courierService.create(any(CreateCourierRequest.class))).thenReturn(courier);

        mockMvc.perform(post("/couriers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("张三"))
                .andExpect(jsonPath("$.data.status").value("AVAILABLE"));
    }

    @Test
    void shouldGetAllCouriers() throws Exception {
        Courier courier1 = new Courier(1L, "张三", "13800000000", CourierStatus.AVAILABLE, null);
        Courier courier2 = new Courier(2L, "李四", "13900000000", CourierStatus.BUSY, null);
        when(courierService.findAll()).thenReturn(List.of(courier1, courier2));

        mockMvc.perform(get("/couriers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].name").value("张三"))
                .andExpect(jsonPath("$.data[1].name").value("李四"));
    }

    @Test
    void shouldGetCourierById() throws Exception {
        Courier courier = new Courier(1L, "王五", "13700000000", CourierStatus.AVAILABLE, null);
        when(courierService.findById(1L)).thenReturn(courier);

        mockMvc.perform(get("/couriers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("王五"))
                .andExpect(jsonPath("$.data.phone").value("13700000000"));
    }

    @Test
    void shouldUpdateCourier() throws Exception {
        UpdateCourierRequest request = new UpdateCourierRequest("张三丰", "13600000000");
        Courier updatedCourier = new Courier(1L, "张三丰", "13600000000", CourierStatus.AVAILABLE, null);

        when(courierService.update(eq(1L), any(UpdateCourierRequest.class))).thenReturn(updatedCourier);

        mockMvc.perform(put("/couriers/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.name").value("张三丰"));
    }

    @Test
    void shouldDeleteCourier() throws Exception {
        doNothing().when(courierService).deleteById(1L);

        mockMvc.perform(delete("/couriers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("删除成功"));
    }
}
