package com.car2hass.vehicle;

import com.car2hass.vehicle.obd.Elm327Session;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/** Plain-Java tests for Elm327Session over piped, scripted streams. */
public class Elm327SessionTest {

    private static final class ScriptedLink {
        final CopyOnWriteArrayList<String> commands = new CopyOnWriteArrayList<>();
        private final PipedOutputStream toIn;
        final InputStream sessionIn;
        final OutputStream sessionOut;

        ScriptedLink(Function<String, String> responder) throws Exception {
            toIn = new PipedOutputStream();
            sessionIn = new PipedInputStream(toIn, 8192);
            PipedOutputStream outPipe = new PipedOutputStream();
            sessionOut = outPipe;
            PipedInputStream fromSession = new PipedInputStream(outPipe, 8192);
            Thread thread = new Thread(() -> {
                try {
                    StringBuilder cmd = new StringBuilder();
                    int b;
                    while ((b = fromSession.read()) != -1) {
                        cmd.append((char) b);
                        if (cmd.length() > 0 && cmd.charAt(cmd.length() - 1) == '\r') {
                            String c = cmd.toString();
                            commands.add(c);
                            String resp = responder.apply(c);
                            if (resp != null) {
                                toIn.write(resp.getBytes(StandardCharsets.US_ASCII));
                                toIn.flush();
                            }
                            cmd.setLength(0);
                        }
                    }
                } catch (Exception ignored) {
                }
            }, "responder");
            thread.setDaemon(true);
            thread.start();
        }

        void close() throws Exception {
            sessionOut.close();
            toIn.close();
        }
    }

    public static void main(String[] args) throws Exception {
        testInitOrderAndVersion();
        testInitRetriesAfterNonElmReply();
        testTransactEmptyIsNull();
        testParseVin();
        System.out.println("All Elm327Session tests passed.");
    }

    private static void testParseVin() {
        // Mode 09 PID 02, multi-frame ("49 02 01" header + VIN bytes).
        String mode09 = "49 02 01 57 50 30 5A 5A 5A 39 39 5A\r"
                + "54 53 33 39 33 30 30 30\r>";
        String vin = Elm327Parser.parseVin(mode09);
        if (!"WP0ZZZ99ZTS393000".equals(vin)) throw new AssertionError("mode09 VIN=" + vin);

        // UDS ReadDataByIdentifier "62 F1 90" + VIN.
        String uds = "62 F1 90 4C 47 58 43 45 45 45 30 34 4D\r"
                + "41 30 30 31 30 30 30\r>";
        String vin2 = Elm327Parser.parseVin(uds);
        if (!"LGXCEEE04MA001000".equals(vin2)) throw new AssertionError("uds VIN=" + vin2);

        // Garbage / no data → null.
        if (Elm327Parser.parseVin("NO DATA\r>") != null) throw new AssertionError("garbage must be null");
        if (Elm327Parser.parseVin(null) != null) throw new AssertionError("null must be null");
    }

    private static void testInitOrderAndVersion() throws Exception {
        ScriptedLink link = new ScriptedLink(cmd -> {
            if (cmd.equals("ATZ\r")) return "ELM327 v1.5\r\r>";
            if (cmd.startsWith("ATE") || cmd.startsWith("ATH") || cmd.startsWith("ATL")
                    || cmd.startsWith("ATSP")) return "OK\r>";
            if (cmd.equals("ATI\r")) return "ELM327 v1.5\r\r>";
            return "?";
        });
        try (Elm327Session s = new Elm327Session(link.sessionIn, link.sessionOut)) {
            String version = s.initWarmUp();
            if (!"V1.5".equals(version)) throw new AssertionError("version=" + version);
        }
        link.close();
        String joined = String.join("", link.commands);
        if (!joined.contains("ATZ\r")) throw new AssertionError("no ATZ");
        if (!joined.contains("ATE0\r")) throw new AssertionError("no ATE0");
        if (!joined.contains("ATSP0\r")) throw new AssertionError("no ATSP0");
        if (!joined.contains("ATI\r")) throw new AssertionError("no ATI");
        System.out.println("init order ok");
    }

    private static void testInitRetriesAfterNonElmReply() throws Exception {
        final int[] ati = {0};
        ScriptedLink link = new ScriptedLink(cmd -> {
            if (cmd.equals("ATI\r")) {
                ati[0]++;
                return ati[0] == 1 ? "OK\r>" : "ELM327 v1.5\r\r>";
            }
            if (cmd.equals("ATZ\r")) return "ELM327 v1.5\r\r>"; // ATZ echoes the version
            if (cmd.startsWith("ATE") || cmd.startsWith("ATH") || cmd.startsWith("ATL")
                    || cmd.startsWith("ATSP")) return "OK\r>";
            return "?";
        });
        try (Elm327Session s = new Elm327Session(link.sessionIn, link.sessionOut)) {
            String version = s.initWarmUp();
            if (!"V1.5".equals(version)) throw new AssertionError("version=" + version);
        }
        link.close();
        if (ati[0] != 2) throw new AssertionError("expected retry, ATI calls=" + ati[0]);
        System.out.println("retry ok");
    }

    private static void testTransactEmptyIsNull() throws Exception {
        ScriptedLink link = new ScriptedLink(cmd -> null); // adapter silent
        try (Elm327Session s = new Elm327Session(link.sessionIn, link.sessionOut)) {
            String resp = s.transact("0100", 1);
            if (resp != null) throw new AssertionError("expected null, got=" + resp);
        }
        link.close();
        System.out.println("empty->null ok");
    }
}