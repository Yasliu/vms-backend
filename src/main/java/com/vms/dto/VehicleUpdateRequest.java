package com.vms.dto;

import com.vms.entity.VehicleStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable vehicle fields. */
public record VehicleUpdateRequest(
        @NotBlank @Size(max = 30) String vehicleNumber,
        @NotBlank @Size(max = 100) String company,
        @NotBlank @Size(max = 50) String type,
        @NotBlank @Size(max = 50) String brand,
        @NotBlank @Size(max = 50) String model,
        @NotNull VehicleStatus status) {
}
