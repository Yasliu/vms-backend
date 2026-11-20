package com.vms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.vms.dto.VehicleCreateRequest;
import com.vms.dto.VehicleResponse;
import com.vms.entity.Vehicle;
import com.vms.entity.VehicleStatus;
import com.vms.exception.DuplicateVehicleNumberException;
import com.vms.repository.VehicleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The rule under test: a vehicle number can be registered only once.
 *
 * <p>The repository is a Mockito mock, so no database, Spring context or network is involved and the
 * results never depend on stored data, time or randomness.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Vehicle number must be unique")
class VehicleNumberUniquenessTest {

    private static final String NUMBER = "DHAKA-11-2345";

    @Mock
    VehicleRepository repository;

    @InjectMocks
    VehicleService service;

    private static VehicleCreateRequest requestFor(String vehicleNumber) {
        return new VehicleCreateRequest(vehicleNumber, "Acme Logistics", "Truck", "Volvo", "FH16", null);
    }

    @Test
    @DisplayName("an existing vehicle number is rejected with DuplicateVehicleNumberException")
    void existingNumberIsRejected() {
        when(repository.existsByVehicleNumber(NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.create(requestFor(NUMBER)))
                .isInstanceOf(DuplicateVehicleNumberException.class)
                .hasMessage("A vehicle with vehicle number " + NUMBER + " already exists")
                .extracting("vehicleNumber").isEqualTo(NUMBER);
    }

    @Test
    @DisplayName("a rejected duplicate is never saved")
    void rejectedDuplicateIsNotSaved() {
        when(repository.existsByVehicleNumber(NUMBER)).thenReturn(true);

        assertThatThrownBy(() -> service.create(requestFor(NUMBER)))
                .isInstanceOf(DuplicateVehicleNumberException.class);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("a new vehicle number is accepted and saved")
    void newNumberIsAccepted() {
        when(repository.existsByVehicleNumber(NUMBER)).thenReturn(false);
        when(repository.saveAndFlush(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VehicleResponse created = service.create(requestFor(NUMBER));

        assertThat(created.vehicleNumber()).isEqualTo(NUMBER);
        assertThat(created.status()).isEqualTo(VehicleStatus.ACTIVE);
        verify(repository).saveAndFlush(any(Vehicle.class));
    }

    @Test
    @DisplayName("the same number typed with different case or spacing counts as a duplicate")
    void formattingDoesNotCreateANewNumber() {
        // Existing vehicles are stored normalized, so the lookup must use the normalized form.
        when(repository.existsByVehicleNumber("DHAKA METRO 11")).thenReturn(true);

        assertThatThrownBy(() -> service.create(requestFor("  dhaka   metro 11 ")))
                .isInstanceOf(DuplicateVehicleNumberException.class);

        verify(repository, never()).saveAndFlush(any());
    }
}
