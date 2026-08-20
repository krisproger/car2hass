package com.diplustohass;

public class UnsupportedRecoveryGateTest {
    public static void main(String[] args) {
        check(UnsupportedRecoveryGate.shouldReisolate(0) == false, "0 failures");
        check(UnsupportedRecoveryGate.shouldReisolate(1) == false, "1 failure");
        check(UnsupportedRecoveryGate.shouldReisolate(4) == false, "4 failures");
        check(UnsupportedRecoveryGate.shouldReisolate(5), "5 failures");
        check(UnsupportedRecoveryGate.shouldReisolate(6) == false, "6 failures");
        check(UnsupportedRecoveryGate.shouldReisolate(10), "10 failures");
        System.out.println("All UnsupportedRecoveryGate tests passed.");
    }

    private static void check(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }
}