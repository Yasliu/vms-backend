package com.vms.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vms.entity.Vehicle;
import com.vms.entity.VehicleStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class VehicleRepositoryTest {

    @Autowired
    VehicleRepository repository;

    private static Vehicle vehicle(String number) {
        return new Vehicle(number, "Acme", "Truck", "Volvo", "FH16", VehicleStatus.ACTIVE);
    }

    @Test
    void databaseRejectsTwoVehiclesWithTheSameNumber() {
        repository.saveAndFlush(vehicle("DHAKA-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(vehicle("DHAKA-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existenceQueriesIgnoreTheVehicleBeingEdited() {
        Vehicle saved = repository.saveAndFlush(vehicle("DHAKA-1"));

        assertThat(repository.existsByVehicleNumber("DHAKA-1")).isTrue();
        assertThat(repository.existsByVehicleNumber("DHAKA-2")).isFalse();
        assertThat(repository.existsByVehicleNumberAndIdNot("DHAKA-1", saved.getId())).isFalse();
        assertThat(repository.existsByVehicleNumberAndIdNot("DHAKA-1", saved.getId() + 1)).isTrue();
    }
}
