package com.car2hass;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Pure gzip helper (java.util.zip) shared by the payload codec and tests. */
public final class GzipCodec {

    private GzipCodec() {}

    public static byte[] compress(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPOutputStream gz = new GZIPOutputStream(bos)) {
                gz.write(data == null ? new byte[0] : data);
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("gzip compress failed", e);
        }
    }

    public static byte[] decompress(byte[] data) {
        try {
            try (GZIPInputStream gz = new GZIPInputStream(
                    new ByteArrayInputStream(data == null ? new byte[0] : data))) {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = gz.read(buf)) > 0) bos.write(buf, 0, n);
                return bos.toByteArray();
            }
        } catch (IOException e) {
            throw new IllegalStateException("gzip decompress failed", e);
        }
    }
}
