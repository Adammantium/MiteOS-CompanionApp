package com.miteos.func;

import android.graphics.Bitmap;import android.graphics.Color;import java.io.IOException;
import java.net.URL;

public class SteinbergDithering {
    static class C3 {
            float r, g, b;

            public C3(Color c) {
                r = c.red();
                g = c.green();
                b = c.blue();
            }

            public C3(float r, float g, float b) {
                this.r = r;
                this.g = g;
                this.b = b;
            }

            public C3 add(C3 o) {
                return new C3(r + o.r, g + o.g, b + o.b);
            }

            public float clamp(float c) {
                return Math.max(0, Math.min(1, c));
            }

            public float diff(C3 o) {
                float Rdiff = o.r - r;
                float Gdiff = o.g - g;
                float Bdiff = o.b - b;
                float distanceSquared = Rdiff * Rdiff + Gdiff * Gdiff + Bdiff * Bdiff;
                return distanceSquared;
            }

            public C3 mul(float d) {
                return new C3(d * r, d * g, d * b);
            }

            public C3 sub(C3 o) {
                return new C3(r - o.r, g - o.g, b - o.b);
            }

            public Color toColor() {
                return Color.valueOf(r, g, b);
            }

            public int toRGB() {
                return Color.rgb(r, g, b);
            }
        }

        private static C3 findClosestPaletteColor(C3 c, C3[] palette) {
            C3 closest = palette[0];

            for (C3 n : palette) {
                if (n.diff(c) < closest.diff(c)) {
                    closest = n;
                }
            }

            return closest;
        }

        public static Bitmap floydSteinbergDithering(Bitmap img) {

            C3[] palette = new C3[] {
                    new C3(  0,   0,   0), // black
                    /*
                    new C3(  0,   0, 1), // green
                    new C3(  0, 1,   0), // blue
                    new C3(  0, 1, 1), // cyan
                    new C3(1,   0,   0), // red
                    new C3(1,   0, 1), // purple
                    new C3(1, 1,   0), // yellow
                    */
                    new C3(1, 1, 1)  // white
            };

            int w = img.getWidth();
            int h = img.getHeight();

            C3[][] d = new C3[h][w];

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    d[y][x] = new C3(img.getColor(x, y));
                }
            }

            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {

                    C3 oldColor = d[y][x];
                    C3 newColor = findClosestPaletteColor(oldColor, palette);
                    img.setPixel(x, y, newColor.toColor().toArgb());

                    C3 err = oldColor.sub(newColor);

                    if (x + 1 < w) {
                        d[y][x + 1] = d[y][x + 1].add(err.mul(7.0f / 16));
                    }

                    if (x - 1 >= 0 && y + 1 < h) {
                        d[y + 1][x - 1] = d[y + 1][x - 1].add(err.mul(3.0f / 16));
                    }

                    if (y + 1 < h) {
                        d[y + 1][x] = d[y + 1][x].add(err.mul(5.0f / 16));
                    }

                    if (x + 1 < w && y + 1 < h) {
                        d[y + 1][x + 1] = d[y + 1][x + 1].add(err.mul(1.0f / 16));
                    }
                }
            }

            return img;
    }
}