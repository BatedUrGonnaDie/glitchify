package com.leagueofnewbs.glitchify;

public class Color {

    private int r;
    private int g;
    private int b;
    private int a;

    public Color(int color) {
        b = color & 255;
        g = (color >> 8) & 255;
        r = (color >> 16) & 255;
        a = (color >> 24) & 255;
    }

    public Integer toInt() {
        Integer rgba = a;
        rgba = (rgba << 8) + r;
        rgba = (rgba << 8) + g;
        rgba = (rgba << 8) + b;
        return rgba;
    }

    public void brighten(double amount) {
        amount = Math.round(255 * (amount / 100));

        r = Math.max(0, Math.min(255, r + (int) amount));
        g = Math.max(0, Math.min(255, g + (int) amount));
        b = Math.max(0, Math.min(255, b + (int) amount));
    }

    public double luminance() {
        double red = bit2linear(r / 255d);
        double green = bit2linear(g / 255d);
        double blue = bit2linear(b / 255d);

        return (0.2126 * red) + (0.7152 * green) + (0.0722 * blue);
    }

    private double bit2linear(double channel) {
        return (channel <= 0.04045) ? channel / 12.92 : Math.pow((channel + 0.055) / 1.055, 2.4);
    }
}
