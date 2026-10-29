package com.car2hass.vehicle;

public final class VoyahIdentityTest {
    public static void main(String[] args) {
        check(VoyahIdentity.evaluate(false, false, null, null, null, false)
                == VoyahIdentity.State.NOT_VOYAH, "no service");
        check(VoyahIdentity.evaluate(true, false, null, null, null, false)
                == VoyahIdentity.State.OTHER_QINGGAN, "service no odo");
        check(VoyahIdentity.evaluate(true, true, "H97X", "VOYAH", 1, false)
                == VoyahIdentity.State.CONFIRMED, "voyah marker");
        check(VoyahIdentity.evaluate(true, true, "default", "default", 134, false)
                == VoyahIdentity.State.CONFIRMED, "observed Voyah vehicle type");
        check(VoyahIdentity.evaluate(true, true, "X", "SGMW", 2, false)
                == VoyahIdentity.State.UNCONFIRMED, "unknown qinggan");
        check(VoyahIdentity.evaluate(true, true, "X", "SGMW", 2, true)
                == VoyahIdentity.State.CONFIRMED, "explicit user");
        System.out.println("All VoyahIdentity tests passed.");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
