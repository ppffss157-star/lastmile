package com.example.logistics.lastmile.entity;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courier", indexes = {
    @Index(name = "idx_courier_status", columnList = "status")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Courier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String phone;

    @Enumerated(EnumType.STRING)
    private CourierStatus status;

    @OneToMany(mappedBy = "courier")
    @JsonIgnoreProperties("courier")  // 序列化时不展开 order 里的 courier，防止死循环
    private List<Order> orders = new ArrayList<>();
}
