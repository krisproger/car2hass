package com.diplustohass.vehicle;

public class VehicleProfileTest {
    public static void main(String[] args) {
        VehicleProfile byd = new VehicleProfile(VehicleProducer.BYD, "Song PRO 2022", "2022");
        check(!byd.isUniversal(), "BYD profile must not be universal");
        check("Song PRO 2022".equals(byd.getMake()), "make preserved");
        check("2022".equals(byd.getYear()), "year preserved");
        check(VehicleProducer.BYD == byd.getProducer(), "producer preserved");

        VehicleProfile uni = new VehicleProfile(VehicleProducer.UNIVERSAL, "Voyah FREE", "2023");
        check(uni.isUniversal(), "universal profile is universal");

        VehicleProfile def = VehicleProfile.defaultProfile();
        check(VehicleProducer.BYD == def.getProducer(), "default producer is BYD");
        System.out.println("All VehicleProfile tests passed.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}