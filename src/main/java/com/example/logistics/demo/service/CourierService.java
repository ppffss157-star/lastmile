package com.example.logistics.demo.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.logistics.demo.dto.CreateCourierRequest;
import com.example.logistics.demo.dto.UpdateCourierRequest;
import com.example.logistics.demo.entity.Courier;
import com.example.logistics.demo.entity.CourierStatus;
import com.example.logistics.demo.exception.CourierNotFoundException;
import com.example.logistics.demo.repository.CourierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepository courierRepository;

    public Courier create(CreateCourierRequest request) {
        Courier courier = new Courier();
        courier.setName(request.getName());
        courier.setPhone(request.getPhone());
        courier.setStatus(CourierStatus.AVAILABLE);

        return courierRepository.save(courier);
    }

    public List<Courier> findAll() {
        return courierRepository.findAll();
    }

    public Courier findById(Long id) {
        return courierRepository.findById(id)
                .orElseThrow(() -> new CourierNotFoundException());
    }

    public Courier update(Long id, UpdateCourierRequest request) {
        Courier courier = courierRepository.findById(id)
                .orElseThrow(() -> new CourierNotFoundException());

        courier.setName(request.getName());
        courier.setPhone(request.getPhone());

        return courierRepository.save(courier);
    }

    public void deleteById(Long id) {
        if (!courierRepository.existsById(id)) {
            throw new CourierNotFoundException();
        }

        courierRepository.deleteById(id);
    }
}