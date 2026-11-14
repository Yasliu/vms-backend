package com.vms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vms.dto.VehicleCreateRequest;
import com.vms.dto.VehicleResponse;
import com.vms.dto.VehicleUpdateRequest;
import com.vms.entity.Vehicle;
import com.vms.entity.VehicleStatus;
import com.vms.exception.DuplicateVehicleNumberException;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.VehicleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class VehicleServiceTest {

    @Mock
    VehicleRepository repository;

    @InjectMocks
    VehicleService service;

    private static VehicleCreateRequest createRequest(String vehicleNumber) {
        return new VehicleCreateRequest(vehicleNumber, "Acme Logistics", "Truck", "Volvo", "FH16", null);
    }

    private static VehicleUpdateRequest updateRequest(String vehicleNumber) {
        return new VehicleUpdateRequest(vehicleNumber, "Acme Logistics", "Truck", "Volvo", "FH16",
                VehicleStatus.ACTIVE);
    }

    private static Vehicle existingVehicle() {
        return new Vehicle("DHAKA-11-2345", "Acme Logistics", "Truck", "Volvo", "FH16", VehicleStatus.ACTIVE);
    }

    @Nested
    class Create {

        @Test
        void savesVehicleWhenNumberIsNew() {
            when(repository.existsByVehicleNumber("DHAKA-11-2345")).thenReturn(false);
            when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

            VehicleResponse response = service.create(createRequest("DHAKA-11-2345"));

            assertThat(response.vehicleNumber()).isEqualTo("DHAKA-11-2345");
            assertThat(response.status()).isEqualTo(VehicleStatus.ACTIVE);
        }

        @Test
        void rejectsDuplicateVehicleNumberAndDoesNotSave() {
            when(repository.existsByVehicleNumber("DHAKA-11-2345")).thenReturn(true);

            assertThatThrownBy(() -> service.create(createRequest("DHAKA-11-2345")))
                    .isInstanceOf(DuplicateVehicleNumberException.class)
                    .hasMessageContaining("DHAKA-11-2345")
                    .extracting("vehicleNumber").isEqualTo("DHAKA-11-2345");

            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        void comparesNormalizedNumberSoCaseAndWhitespaceDoNotBypassTheRule() {
            when(repository.existsByVehicleNumber("DHAKA METRO 11")).thenReturn(true);

            assertThatThrownBy(() -> service.create(createRequest("  dhaka   metro 11 ")))
                    .isInstanceOf(DuplicateVehicleNumberException.class);

            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        void translatesDatabaseUniqueViolationFromConcurrentRequestIntoSameError() {
            when(repository.existsByVehicleNumber("DHAKA-11-2345")).thenReturn(false);
            DataIntegrityViolationException dbError = new DataIntegrityViolationException("uk_vehicles_vehicle_number");
            when(repository.saveAndFlush(any(Vehicle.class))).thenThrow(dbError);

            assertThatThrownBy(() -> service.create(createRequest("DHAKA-11-2345")))
                    .isInstanceOf(DuplicateVehicleNumberException.class)
                    .hasCause(dbError);
        }
    }

    @Nested
    class Update {

        @Test
        void rejectsNumberBelongingToAnotherVehicle() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingVehicle()));
            when(repository.existsByVehicleNumberAndIdNot("CTG-22-0001", 1L)).thenReturn(true);

            assertThatThrownBy(() -> service.update(1L, updateRequest("ctg-22-0001")))
                    .isInstanceOf(DuplicateVehicleNumberException.class)
                    .hasMessageContaining("CTG-22-0001");

            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        void allowsVehicleToKeepItsOwnNumber() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingVehicle()));
            when(repository.existsByVehicleNumberAndIdNot("DHAKA-11-2345", 1L)).thenReturn(false);
            when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(inv -> inv.getArgument(0));

            VehicleResponse response = service.update(1L, updateRequest("DHAKA-11-2345"));

            assertThat(response.vehicleNumber()).isEqualTo("DHAKA-11-2345");
        }

        @Test
        void translatesDatabaseUniqueViolationFromConcurrentRequestIntoSameError() {
            when(repository.findById(1L)).thenReturn(Optional.of(existingVehicle()));
            when(repository.existsByVehicleNumberAndIdNot("CTG-22-0001", 1L)).thenReturn(false);
            when(repository.saveAndFlush(any(Vehicle.class))).thenThrow(new DataIntegrityViolationException("dup"));

            assertThatThrownBy(() -> service.update(1L, updateRequest("CTG-22-0001")))
                    .isInstanceOf(DuplicateVehicleNumberException.class);
        }

        @Test
        void failsWithNotFoundBeforeCheckingDuplicates() {
            when(repository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(99L, updateRequest("CTG-22-0001")))
                    .isInstanceOf(ResourceNotFoundException.class);

            verify(repository, never()).existsByVehicleNumberAndIdNot(any(), any());
        }
    }

    @Test
    void normalizeTrimsCollapsesWhitespaceAndUpperCases() {
        assertThat(VehicleService.normalizeVehicleNumber("  dhaka \t metro-ga   11 ")).isEqualTo("DHAKA METRO-GA 11");
    }
}
