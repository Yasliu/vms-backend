package com.vms.dto;

import com.vms.entity.VehicleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code status} is optional and defaults to ACTIVE. */
public record VehicleCreateRequest(
        @NotBlank @Size(max = 30) String vehicleNumber,
        @NotBlank @Size(max = 100) String company,
        @NotBlank @Size(max = 50) String type,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        VehicleStatus status) {
}
