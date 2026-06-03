package com.example.logistics.lastmile.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    @NotBlank(message = "客户姓名不能为空")
    private String customerName;

    @NotBlank(message = "地址不能为空")
    private String address;

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
