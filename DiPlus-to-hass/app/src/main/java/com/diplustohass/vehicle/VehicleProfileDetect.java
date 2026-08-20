package com.diplustohass.vehicle;

import java.util.Map;

/** Определяет производителя/марку/год из системных свойств (getprop). */
public final class VehicleProfileDetect {

    private VehicleProfileDetect() {}

    public static class Result {
        private final String producerHint;
        private final String makeHint;
        private final String yearHint;

        Result(String producerHint, String makeHint, String yearHint) {
            this.producerHint = producerHint != null ? producerHint : "";
            this.makeHint = makeHint != null ? makeHint : "";
            this.yearHint = yearHint != null ? yearHint : "";
        }

        public String getProducerHint() { return producerHint; }
        public String getMakeHint() { return makeHint; }
        public String getYearHint() { return yearHint; }
        public boolean isEmpty() {
            return producerHint.isEmpty() && makeHint.isEmpty() && yearHint.isEmpty();
        }
    }

    public static Result detect(Map<String, String> props) {
        String manufacturer = first(props, "ro.product.manufacturer", "ro.product.brand", "ro.product.name");
        String vehicleType = first(props, "ro.vehicle.type", "ro.vehicle.type.value", "persist.sys.vehicle_sales_record");
        String buildDate = first(props, "ro.build.date");
        String yearHint = "";
        if (buildDate != null && buildDate.length() >= 4) {
            String four = buildDate.substring(0, 4);
            if (four.matches("\\d{4}")) yearHint = four;
        }
        String makeHint = manufacturer != null ? manufacturer : vehicleType;
        String producerHint = vehicleType != null ? vehicleType : manufacturer;
        return new Result(producerHint != null ? producerHint : "",
                          makeHint != null ? makeHint : "",
                          yearHint);
    }

    private static String first(Map<String, String> props, String... keys) {
        for (String k : keys) {
            String v = props.get(k);
            if (v != null && !v.trim().isEmpty() && !"---".equals(v.trim())) {
                return v.trim();
            }
        }
        return null;
    }
}