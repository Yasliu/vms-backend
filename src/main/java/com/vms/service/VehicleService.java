package com.vms.service;

import com.vms.dto.PageResponse;
import com.vms.dto.VehicleCreateRequest;
import com.vms.dto.VehicleResponse;
import com.vms.dto.VehicleUpdateRequest;
import com.vms.entity.Vehicle;
import com.vms.entity.VehicleStatus;
import com.vms.exception.DuplicateVehicleNumberException;
import com.vms.exception.ResourceNotFoundException;
import com.vms.repository.VehicleRepository;
import com.vms.repository.VehicleSpecifications;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules:
 * <ul>
 *   <li>Vehicle number is unique. It is normalised (trimmed, inner whitespace collapsed, upper-cased)
 *       before every check, so "dhaka  1" and "DHAKA 1" are the same vehicle.</li>
 *   <li>Status defaults to ACTIVE when not provided on creation.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class VehicleService {

    private final VehicleRepository repository;

    public VehicleService(VehicleRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public VehicleResponse create(VehicleCreateRequest request) {
        String vehicleNumber = normalizeVehicleNumber(request.vehicleNumber());
        if (repository.existsByVehicleNumber(vehicleNumber)) {
            throw new DuplicateVehicleNumberException(vehicleNumber);
        }

        Vehicle vehicle = new Vehicle(vehicleNumber, request.company().trim(), request.type().trim(),
                request.brand().trim(), request.model().trim(),
                request.status() != null ? request.status() : VehicleStatus.ACTIVE);
        return VehicleResponse.from(saveEnforcingUniqueness(vehicle));
    }

    public VehicleResponse get(Long id) {
        return VehicleResponse.from(find(id));
    }

    public PageResponse<VehicleResponse> search(VehicleStatus status, String text, Pageable pageable) {
        var spec = VehicleSpecifications.hasStatus(status).and(VehicleSpecifications.matchesText(text));
        return PageResponse.from(repository.findAll(spec, pageable), VehicleResponse::from);
    }

    @Transactional
    public VehicleResponse update(Long id, VehicleUpdateRequest request) {
        Vehicle vehicle = find(id);
        String vehicleNumber = normalizeVehicleNumber(request.vehicleNumber());
        if (repository.existsByVehicleNumberAndIdNot(vehicleNumber, id)) {
            throw new DuplicateVehicleNumberException(vehicleNumber);
        }

        vehicle.setVehicleNumber(vehicleNumber);
        vehicle.setCompany(request.company().trim());
        vehicle.setType(request.type().trim());
        vehicle.setBrand(request.brand().trim());
        vehicle.setModel(request.model().trim());
        vehicle.setStatus(request.status());
        return VehicleResponse.from(saveEnforcingUniqueness(vehicle));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(find(id));
    }

    private Vehicle find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle", id));
    }

    /**
     * The existence check above can lose a race against a concurrent request; the database unique
     * constraint is the final guard, and its violation is reported as the same domain error.
     */
    private Vehicle saveEnforcingUniqueness(Vehicle vehicle) {
        try {
            return repository.saveAndFlush(vehicle);
        } catch (DataIntegrityViolationException ex) {
            throw new DuplicateVehicleNumberException(vehicle.getVehicleNumber(), ex);
        }
    }

    static String normalizeVehicleNumber(String raw) {
        return raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }
}
