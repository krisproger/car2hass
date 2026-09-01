package com.car2hass.vehicle;

import java.util.List;

public class ObdCodecTest {
    public static void main(String[] args) throws Exception {
        // ATI response
        if (!Elm327Parser.isAtiResponse("ELM327 v1.5\r>")) throw new AssertionError("ati");
        String v = Elm327Parser.extractVersion("SEARCHING...\rELM327 v1.5\r\r>");
        if (!"V1.5".equals(v)) throw new AssertionError("version=" + v);
        if (Elm327Parser.isAtiResponse("NO DATA")) throw new AssertionError("no data is not elm");

        // splitLines normalization
        List<String> lines = Elm327Parser.splitLines("41 0D 50\r\r>");
        if (lines.size() != 1 || !"41 0D 50".equals(lines.get(0))) throw new AssertionError("lines=" + lines);

        // speed: 41 0D 50 -> 80
        Integer speed = ObdPidCodec.parse("010D", "010D\r41 0D 50\r\r>");
        if (speed == null || speed != 80) throw new AssertionError("speed=" + speed);

        // rpm: 41 0C 1A F8 -> (0x1A*256+0xF8)/4 = 1726
        Integer rpm = ObdPidCodec.parse("010C", "41 0C 1A F8\r>");
        if (rpm == null || rpm != 1726) throw new AssertionError("rpm=" + rpm);

        // coolant: 41 05 5A -> 90-40=50
        Integer cool = ObdPidCodec.parse("0105", "41 05 5A");
        if (cool == null || cool != 50) throw new AssertionError("cool=" + cool);

        // engine_load: 41 04 80 -> 128*100/255 = 50
        Integer load = ObdPidCodec.parse("0104", "41 04 80");
        if (load == null || load != 50) throw new AssertionError("load=" + load);

        // intake air temp: 41 0F 4B -> 75-40=35
        Integer iat = ObdPidCodec.parse("010F", "41 0F 4B");
        if (iat == null || iat != 35) throw new AssertionError("iat=" + iat);

        // maf: 41 10 03 E8 -> (0x03*256+0xE8)/100 = (768+232)/100 = 10
        Integer maf = ObdPidCodec.parse("0110", "41 10 03 E8");
        if (maf == null || maf != 10) throw new AssertionError("maf=" + maf);

        // fuel_level: 41 2F 80 -> 128*100/255 = 50
        Integer fuel = ObdPidCodec.parse("012F", "41 2F 80");
        if (fuel == null || fuel != 50) throw new AssertionError("fuel=" + fuel);

        // ambient: 41 46 28 -> 40-40=0
        Integer amb = ObdPidCodec.parse("0146", "41 46 28");
        if (amb == null || amb != 0) throw new AssertionError("amb=" + amb);

        // oil temp: 41 5C 55 -> 85-40=45
        Integer oil = ObdPidCodec.parse("015C", "41 5C 55");
        if (oil == null || oil != 45) throw new AssertionError("oil=" + oil);

        // fuel rate: 41 5E 01 F4 -> (0x01*256+0xF4)/20 = (256+244)/20 = 25
        Integer rate = ObdPidCodec.parse("015E", "41 5E 01 F4");
        if (rate == null || rate != 25) throw new AssertionError("rate=" + rate);

        // errors / garbage
        if (ObdPidCodec.parse("010D", "NO DATA") != null) throw new AssertionError("no data");
        if (ObdPidCodec.parse("010D", "garbage") != null) throw new AssertionError("garbage");

        // catalog consistency
        if (!"speed".equals(ObdPidCodec.keyFor("010D"))) throw new AssertionError("keyFor");
        if (!"fuel_rate".equals(ObdPidCodec.keyFor("015E"))) throw new AssertionError("keyFor 015E");
        if (ObdPidCodec.PID_TO_KEY.size() < 10) throw new AssertionError("catalog size");
        System.out.println("All OBD codec tests passed.");
    }
}
