package com.leagueofnewbs.glitchify;

import java.util.concurrent.ConcurrentHashMap;

class ColorHelper {

    private static final ColorHelper INSTANCE = new ColorHelper();
    private final ConcurrentHashMap<Integer, Integer> cache = new ConcurrentHashMap<>();

    private ColorHelper() {}

    static ColorHelper getInstance() {
        return INSTANCE;
    }

    // All brighten code taken from FFZ, and adapted for java/android
    // https://github.com/FrankerFaceZ/FrankerFaceZ
    Integer maybeBrighten(int color, boolean dark) {
        Integer cachedColor = cache.get(color);
        if (cachedColor != null) {
            return cachedColor;
        }
        Color outputColor = new Color(color);
        int i = 0;
        if (dark) {
            while (outputColor.luminance() < 0.15 && i++ < 127) {
                outputColor.brighten(1);
            }
        } else {
            while (outputColor.luminance() > 0.3 && i++ < 127) {
                outputColor.brighten(-1);
            }
        }

        cache.put(color, outputColor.toInt());
        return outputColor.toInt();
    }
}
