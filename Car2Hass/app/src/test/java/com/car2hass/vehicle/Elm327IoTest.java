package com.car2hass.vehicle;

import com.car2hass.vehicle.obd.Elm327Io;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Elm327IoTest {
    public static void main(String[] args) throws Exception {
        InputStream failing = new InputStream() {
            @Override public int read() throws IOException { throw new IOException("read failed, socket might closed"); }
            @Override public int read(byte[] b, int off, int len) throws IOException { throw new IOException("read failed, socket might closed"); }
            @Override public int available() { return 1; }
        };
        OutputStream sink = new OutputStream() {
            @Override public void write(int b) {}
        };
        try {
            Elm327Io.transact(failing, sink, "0100", 1);
            throw new AssertionError("expected IOException to propagate");
        } catch (IOException expected) {
            System.out.println("All Elm327Io tests passed.");
        }
    }
}
