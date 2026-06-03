package com.example.logistics.lastmile.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.logistics.lastmile.dto.CreateCourierRequest;
import com.example.logistics.lastmile.dto.UpdateCourierRequest;
import com.example.logistics.lastmile.entity.Courier;
import com.example.logistics.lastmile.entity.CourierStatus;
import com.example.logistics.lastmile.exception.CourierNotFoundException;
import com.example.logistics.lastmile.repository.CourierRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CourierService {

    private final CourierRepository courierRepository;

    @Transactional
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

    @Transactional
    public Courier update(Long id, UpdateCourierRequest request) {
        Courier courier = courierRepository.findById(id)
                .orElseThrow(() -> new CourierNotFoundException());

        courier.setName(request.getName());
        courier.setPhone(request.getPhone());

        return courierRepository.save(courier);
    }

    @Transactional
    public void deleteById(Long id) {
        if (!courierRepository.existsById(id)) {
            throw new CourierNotFoundException();
        }

        courierRepository.deleteById(id);
    }
}