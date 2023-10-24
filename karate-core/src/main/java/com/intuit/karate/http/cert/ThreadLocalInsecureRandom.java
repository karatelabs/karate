package com.intuit.karate.http.cert;

import io.netty.util.internal.PlatformDependent;
import java.security.SecureRandom;
import java.util.Random;

final class ThreadLocalInsecureRandom extends SecureRandom {
    private static final long serialVersionUID = -8209473337192526191L;
    private static final SecureRandom INSTANCE = new ThreadLocalInsecureRandom();

    static SecureRandom current() {
        return INSTANCE;
    }

    private ThreadLocalInsecureRandom() {
    }

    public String getAlgorithm() {
        return "insecure";
    }

    public void setSeed(byte[] seed) {
    }

    public void setSeed(long seed) {
    }

    public void nextBytes(byte[] bytes) {
        random().nextBytes(bytes);
    }

    public byte[] generateSeed(int numBytes) {
        byte[] seed = new byte[numBytes];
        random().nextBytes(seed);
        return seed;
    }

    public int nextInt() {
        return random().nextInt();
    }

    public int nextInt(int n) {
        return random().nextInt(n);
    }

    public boolean nextBoolean() {
        return random().nextBoolean();
    }

    public long nextLong() {
        return random().nextLong();
    }

    public float nextFloat() {
        return random().nextFloat();
    }

    public double nextDouble() {
        return random().nextDouble();
    }

    public double nextGaussian() {
        return random().nextGaussian();
    }

    private static Random random() {
        return PlatformDependent.threadLocalRandom();
    }
}