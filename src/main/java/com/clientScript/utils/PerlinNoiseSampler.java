package com.clientScript.utils;

import java.util.Map;
import java.util.Random;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

public class PerlinNoiseSampler {
    protected static final int[][] gradients3d = new int[][]{
        {1, 1, 0}, {-1, 1, 0}, {1, -1, 0}, {-1, -1, 0},
        {1, 0, 1}, {-1, 0, 1}, {1, 0, -1}, {-1, 0, -1},
        {0, 1, 1}, {0, -1, 1}, {0, 1, -1}, {0, -1, -1},
        {1, 1, 0}, {0, -1, 1}, {-1, 1, 0}, {0, -1, -1}
    };
    protected static final int[][] gradients2d = new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}};

    public static PerlinNoiseSampler instance = new PerlinNoiseSampler(new Random(0));
    public static Map<Long, PerlinNoiseSampler> samplers = new Long2ObjectOpenHashMap<>();
    public final double originX;
    public final double originY;
    public final double originZ;
    private final byte[] permutations;

    public PerlinNoiseSampler(Random random) {
        this.originX = random.nextDouble() * 256.0D;
        this.originY = random.nextDouble() * 256.0D;
        this.originZ = random.nextDouble() * 256.0D;
        this.permutations = new byte[256];

        int j;
        for(j=0; j<256; j++)
            this.permutations[j] = (byte)j;
        for(j=0; j<256; j++) {
            int k = random.nextInt(256 - j);
            byte b = this.permutations[j];
            this.permutations[j] = this.permutations[j + k];
            this.permutations[j + k] = b;
        }
    }

    public static PerlinNoiseSampler getPerlin(long aLong) {
        if (PerlinNoiseSampler.samplers.size() > 256)
            PerlinNoiseSampler.samplers.clear();
        return PerlinNoiseSampler.samplers.computeIfAbsent(aLong, seed -> new PerlinNoiseSampler(new Random(seed)));
    }

    public double sample1d(double x) {
        double f = x + this.originX;
        int i = floor(f);
        double l = f - (double)i;
        double o = perlinFade(l);
        return sample1d(i, l, o) + 0.5;
    }

    private double sample1d(int sectionX, double localX, double fadeLocalX) {
        double d = grad1d(getGradient(sectionX), localX);
        double e = grad1d(getGradient(sectionX + 1), localX - 1.0D);
        return lerp(fadeLocalX, d, e);
    }

    public double sample2d(double x, double y) {
        double f = x + this.originX;
        double g = y + this.originY;
        int i = floor(f);
        int j = floor(g);
        double l = f - (double)i;
        double m = g - (double)j;
        double o = perlinFade(l);
        double p = perlinFade(m);
        return sample2d(i, j, l, m, o, p) / 2 + 0.5;
    }

    private double sample2d(int sectionX, int sectionY, double localX, double localY, double fadeLocalX, double fadeLocalY) {
        int j = getGradient(sectionX) + sectionY;
        int m = getGradient(sectionX + 1) + sectionY;
        double d = grad2d(getGradient(j), localX, localY);
        double e = grad2d(getGradient(m), localX - 1.0D, localY);
        double f = grad2d(getGradient(j + 1), localX, localY - 1.0D);
        double g = grad2d(getGradient(m + 1), localX - 1.0D, localY - 1.0D);
        return lerp2(fadeLocalX, fadeLocalY, d, e, f, g);
    }

    public double sample3d(double x, double y, double z) {
        double f = x + this.originX;
        double g = y + this.originY;
        double h = z + this.originZ;
        int i = floor(f);
        int j = floor(g);
        int k = floor(h);
        double l = f - (double)i;
        double m = g - (double)j;
        double n = h - (double)k;
        double o = perlinFade(l);
        double p = perlinFade(m);
        double q = perlinFade(n);
        return sample3d(i, j, k, l, m, n, o, p, q) / 2 + 0.5;
    }

    private double sample3d(int sectionX, int sectionY, int sectionZ, double localX, double localY, double localZ, double fadeLocalX, double fadeLocalY, double fadeLocalZ) {
        int i = getGradient(sectionX) + sectionY;
        int j = getGradient(i) + sectionZ;
        int k = getGradient(i + 1) + sectionZ;
        int l = getGradient(sectionX + 1) + sectionY;
        int m = getGradient(l) + sectionZ;
        int n = getGradient(l + 1) + sectionZ;
        double d = grad3d(getGradient(j), localX, localY, localZ);
        double e = grad3d(getGradient(m), localX - 1.0D, localY, localZ);
        double f = grad3d(getGradient(k), localX, localY - 1.0D, localZ);
        double g = grad3d(getGradient(n), localX - 1.0D, localY - 1.0D, localZ);
        double h = grad3d(getGradient(j + 1), localX, localY, localZ - 1.0D);
        double o = grad3d(getGradient(m + 1), localX - 1.0D, localY, localZ - 1.0D);
        double p = grad3d(getGradient(k + 1), localX, localY - 1.0D, localZ - 1.0D);
        double q = grad3d(getGradient(n + 1), localX - 1.0D, localY - 1.0D, localZ - 1.0D);
        return lerp3(fadeLocalX, fadeLocalY, fadeLocalZ, d, e, f, g, h, o, p, q);
    }

    public static int floor(double d) {
        int i = (int)d;
        return d < (double)i ? i - 1 : i;
    }

    public static double perlinFade(double d) {
        return d * d * d * (d * (d * 6.0D - 15.0D) + 10.0D);
    }

    public int getGradient(int hash) {
        return this.permutations[hash & 255] & 255;
    }

    private static double grad1d(int hash, double x) {
        return (hash & 1) == 0 ? x : -x;
    }

    public static double lerp(double delta, double first, double second) {
        return first + delta * (second - first);
    }

    private static double grad2d(int hash, double x, double y) {
        return dot2d(PerlinNoiseSampler.gradients2d[hash & 3], x, y);
    }

    protected static double dot2d(int[] gArr, double x, double y) {
        return (double)gArr[0] * x + (double)gArr[1] * y;
    }

    public static double lerp2(double deltaX, double deltaY, double d, double e, double f, double g) {
        return lerp(deltaY, lerp(deltaX, d, e), lerp(deltaX, f, g));
    }

    private static double grad3d(int hash, double x, double y, double z) {
        return dot3d(PerlinNoiseSampler.gradients3d[hash & 15], x, y, z);
    }

    protected static double dot3d(int[] gArr, double x, double y, double z) {
        return (double)gArr[0] * x + (double)gArr[1] * y + (double)gArr[2] * z;
    }

    public static double lerp3(double deltaX, double deltaY, double deltaZ, double d, double e, double f, double g, double h, double i, double j, double k) {
        return lerp(deltaZ, lerp2(deltaX, deltaY, d, e, f, g), lerp2(deltaX, deltaY, h, i, j, k));
    }
}
