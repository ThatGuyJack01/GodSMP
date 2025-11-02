package io.github.apace100.origins.util;

import net.minecraft.item.MusicDiscItem;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PortableJukeboxHelper {
    private static final int DEFAULT_PLAYBACK_INTERVAL = 2400;
    private static final Method SONG_LENGTH_METHOD;
    private static final Field SONG_LENGTH_FIELD;

    static {
        Method method = null;
        Field field = null;

        try {
            method = MusicDiscItem.class.getMethod("getSongLengthInTicks");
        } catch (NoSuchMethodException ignored) {
            try {
                method = MusicDiscItem.class.getMethod("getSongLength");
            } catch (NoSuchMethodException ignored1) {
                try {
                    field = MusicDiscItem.class.getDeclaredField("songLengthInTicks");
                    field.setAccessible(true);
                } catch (NoSuchFieldException ignored2) {
                    field = null;
                }
            }
        }

        SONG_LENGTH_METHOD = method;
        SONG_LENGTH_FIELD = field;
    }

    private PortableJukeboxHelper() {}

    public static int getPlaybackInterval(MusicDiscItem disc) {
        if (disc == null) {
            return DEFAULT_PLAYBACK_INTERVAL;
        }

        if (SONG_LENGTH_METHOD != null) {
            try {
                Object result = SONG_LENGTH_METHOD.invoke(disc);
                if (result instanceof Integer length && length > 0) {
                    return length;
                }
            } catch (ReflectiveOperationException ignored) {
            }
        }

        if (SONG_LENGTH_FIELD != null) {
            try {
                Object value = SONG_LENGTH_FIELD.get(disc);
                if (value instanceof Integer length && length > 0) {
                    return length;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        return DEFAULT_PLAYBACK_INTERVAL;
    }
}
