package com.vms.exception;

/** Another vehicle already uses this vehicle number. Mapped to HTTP 409. */
public class DuplicateVehicleNumberException extends RuntimeException {

    private final String vehicleNumber;

    public DuplicateVehicleNumberException(String vehicleNumber) {
        super("A vehicle with vehicle number " + vehicleNumber + " already exists");
        this.vehicleNumber = vehicleNumber;
    }

    public DuplicateVehicleNumberException(String vehicleNumber, Throwable cause) {
        this(vehicleNumber);
        initCause(cause);
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }
}
