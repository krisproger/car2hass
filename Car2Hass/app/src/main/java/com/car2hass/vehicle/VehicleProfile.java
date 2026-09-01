package com.car2hass.vehicle;

/** Профиль автомобиля: производитель, марка/модель, год выпуска. */
public class VehicleProfile {
    private final VehicleProducer producer;
    private final String make;
    private final String year;

    public VehicleProfile(VehicleProducer producer, String make, String year) {
        this.producer = producer != null ? producer : VehicleProducer.BYD;
        this.make = make != null ? make : "";
        this.year = year != null ? year : "";
    }

    public static VehicleProfile defaultProfile() {
        return new VehicleProfile(VehicleProducer.BYD, "", "");
    }

    public VehicleProducer getProducer() { return producer; }
    public String getMake() { return make; }
    public String getYear() { return year; }
    public boolean isUniversal() { return producer == VehicleProducer.UNIVERSAL; }
}