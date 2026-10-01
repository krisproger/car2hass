package com.car2hass.vehicle;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;

public class ObdErrorTest {
    public static void main(String[] args) {
        // ExecutionException wraps the real cause (FutureTask in BtSppTransport).
        Throwable wrapped = new ExecutionException(new IOException("read failed, socket might closed or timeout"));
        if (!(ObdError.unwrap(wrapped) instanceof IOException))
            throw new AssertionError("unwrap must return the IOException cause");

        if (!ObdError.BT_OFF.equals(ObdError.classify("open", new IllegalStateException("bluetooth off"))))
            throw new AssertionError("bluetooth off -> bt_off");
        if (!ObdError.BT_NOT_PAIRED.equals(ObdError.classify("open", new IllegalArgumentException("bad address X"))))
            throw new AssertionError("bad address -> bt_not_paired");
        if (!ObdError.BT_CONNECT_TIMEOUT.equals(ObdError.classify("open", new SocketTimeoutException("bt connect timeout (8000ms)"))))
            throw new AssertionError("timeout -> bt_connect_timeout");
        if (!ObdError.BT_CONNECT_IO.equals(ObdError.classify("open", wrapped)))
            throw new AssertionError("open IOException -> bt_connect_io");
        if (!ObdError.IO_ERROR.equals(ObdError.classify("read", new IOException("broken pipe"))))
            throw new AssertionError("read IOException -> io_error");
        if (!ObdError.UNKNOWN.equals(ObdError.classify("read", new RuntimeException("boom"))))
            throw new AssertionError("unexpected -> unknown");
        System.out.println("All ObdError tests passed.");
    }
}
