package com.car2hass.vehicle;

/** Pure decoders for the read-only Voyah direct-binder snapshots. */
public final class VoyahDirectCodec {
    private VoyahDirectCodec() {}

    public static final class Doors {
        public final boolean valid;
        public final boolean trunkValid;
        public final boolean bonnet, frontLeft, frontRight, rearLeft, rearRight, trunk;

        private Doors(boolean valid, boolean trunkValid, boolean bonnet,
                      boolean frontLeft, boolean frontRight, boolean rearLeft,
                      boolean rearRight, boolean trunk) {
            this.valid = valid;
            this.trunkValid = trunkValid;
            this.bonnet = bonnet;
            this.frontLeft = frontLeft;
            this.frontRight = frontRight;
            this.rearLeft = rearLeft;
            this.rearRight = rearRight;
            this.trunk = trunk;
        }

        static Doors invalid() {
            return new Doors(false, false, false, false, false, false, false, false);
        }
    }

    public static final class Fuel {
        public final boolean valid;
        public final float percent;
        Fuel(boolean valid, float percent) { this.valid = valid; this.percent = percent; }
    }

    public static final class Battery {
        public final boolean valid;
        public final float volts;
        Battery(boolean valid, float volts) { this.valid = valid; this.volts = volts; }
    }

    public static final class Odometer {
        public final boolean valid;
        public final float total, canTravel, evRange;
        Odometer(boolean valid, float total, float canTravel, float evRange) {
            this.valid = valid;
            this.total = total;
            this.canTravel = canTravel;
            this.evRange = evRange;
        }
    }

    public static Doors doors(int[] raw) {
        if (raw == null) return Doors.invalid();
        if (raw.length == 6) {
            boolean sideValid = raw[1] >= 0 && raw[2] >= 0 && raw[4] >= 0 && raw[5] >= 0;
            return new Doors(sideValid, raw[3] >= 0, raw[0] > 0,
                    raw[1] > 0, raw[2] > 0, raw[4] > 0, raw[5] > 0, raw[3] > 0);
        }
        if (raw.length == 10) {
            boolean sideValid = raw[1] >= 0 && raw[3] >= 0 && raw[6] >= 0 && raw[8] >= 0;
            return new Doors(sideValid, raw[5] >= 0, raw[0] > 0,
                    raw[1] > 0, raw[3] > 0, raw[6] > 0, raw[8] > 0, raw[5] > 0);
        }
        return Doors.invalid();
    }

    public static Fuel fuel(int capacity, int remain, float percent, int shortage, float... rest) {
        if (!Float.isFinite(percent) || percent < 0f || percent > 100f) {
            return new Fuel(false, 0f);
        }
        return new Fuel(true, percent);
    }

    public static Battery battery(int level, int raw) {
        if (raw < 0) return new Battery(false, 0f);
        float volts = raw > 100 ? raw / 10.0f : (float) raw;
        if (!Float.isFinite(volts) || volts <= 0f || volts > 30f) {
            return new Battery(false, 0f);
        }
        return new Battery(true, volts);
    }

    public static Odometer odometer(float[] raw) {
        if (raw == null || raw.length < 6) return new Odometer(false, 0f, 0f, 0f);
        float total = raw[5];
        float canTravel = raw[0];
        float evRange = raw[1];
        boolean valid = Float.isFinite(total) && total > 0f
                && Float.isFinite(canTravel) && canTravel >= 0f
                && Float.isFinite(evRange) && evRange >= 0f;
        return new Odometer(valid, total, canTravel, evRange);
    }
}
