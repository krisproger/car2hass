package com.diplustohass.vehicle;

import android.content.Context;
import com.diplustohass.AppInfo;
import com.diplustohass.LogBuffer;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Запускает зондирование каналов и формирует отчёт исследования. */
public final class VehicleResearch {

    private VehicleResearch() {}

    /** Колбэк прогресса зондирования: вызывается после каждого канала. */
    public interface ProgressListener {
        void onChannelDone(int done, int total, String displayName);
    }

    public static List<ChannelResult> run(Context ctx, List<DataChannel> channels, VehicleProfile profile) {
        return run(ctx, channels, profile, null);
    }

    public static List<ChannelResult> run(Context ctx, List<DataChannel> channels,
                                          VehicleProfile profile, ProgressListener listener) {
        List<ChannelResult> results = new ArrayList<>();
        int total = channels.size();
        for (int i = 0; i < total; i++) {
            DataChannel ch = channels.get(i);
            ChannelResult r = probeWithTimeout(ch, ctx);
            results.add(r);
            LogBuffer.i("VehicleResearch", "Канал '" + ch.displayName() + "': " + r.summary());
            if (listener != null) listener.onChannelDone(i + 1, total, ch.displayName());
        }
        return results;
    }

    private static ChannelResult probeWithTimeout(DataChannel ch, Context ctx) {
        final ChannelResult[] holder = new ChannelResult[1];
        Thread t = new Thread(() -> holder[0] = ch.probe(ctx), "probe-" + ch.id());
        t.setDaemon(true);
        t.start();
        try {
            t.join(15000); // таймаут на канал
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (holder[0] == null) {
            return ChannelResult.dead("таймаут зондирования канала");
        }
        return holder[0];
    }

    /** Краткая сводка для диалога. */
    public static String summary(List<ChannelResult> results) {
        StringBuilder sb = new StringBuilder();
        for (ChannelResult r : results) {
            sb.append(r.summary()).append('\n');
        }
        return sb.toString();
    }

    /** Полный отчёт с профилем авто в начале; возвращает имя файла. */
    public static String writeReport(Context ctx, VehicleProfile profile, List<ChannelResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Исследование каналов DiPlus-to-hass v")
          .append(AppInfo.getVersionString(ctx)).append(" ===\n");
        sb.append("Производитель: ").append(profile.getProducer().name()).append('\n');
        sb.append("Марка/модель: ").append(profile.getMake()).append('\n');
        sb.append("Год выпуска: ").append(profile.getYear()).append('\n');
        sb.append("Время: ").append(new Date().toString()).append("\n\n");
        for (ChannelResult r : results) {
            sb.append(r.summary()).append('\n');
            for (String e : r.getErrors()) {
                sb.append("  ошибка: ").append(e).append('\n');
            }
        }
        String name = "research_" + safeName(profile.getMake()) + "_"
                + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".txt";
        File dir = ctx.getExternalFilesDir(null);
        if (dir != null) {
            File f = new File(dir, name);
            try (FileOutputStream fos = new FileOutputStream(f)) {
                fos.write(sb.toString().getBytes("UTF-8"));
                LogBuffer.i("VehicleResearch", "Отчёт исследования: " + f.getAbsolutePath());
                return f.getAbsolutePath();
            } catch (Exception e) {
                LogBuffer.e("VehicleResearch", "writeReport: " + e.getMessage());
            }
        }
        return null;
    }

    private static String safeName(String s) {
        if (s == null) return "auto";
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}