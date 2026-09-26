package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;

/**
 * 在六色量化前提升中间调和阴影，并以自然饱和度增强有色区域。
 * <p>
 * 核心优化特性：
 * - 曝光调整有上限，高光使用软肩曲线；
 * - 黑色和低彩度区域不会被强行染色；
 * - 自带肤色保护，避免人像面部过度泛红或泛黄。
 */
public class ImagePreprocessor {

    private static class Profile {
        final double targetMedian;
        final double maxExposureEv;
        final double gamma;
        final double shadowLift;
        final double vibrance;

        Profile(double targetMedian, double maxExposureEv, double gamma, double shadowLift, double vibrance) {
            this.targetMedian = targetMedian;
            this.maxExposureEv = maxExposureEv;
            this.gamma = gamma;
            this.shadowLift = shadowLift;
            this.vibrance = vibrance;
        }
    }

    private static final Profile NATURAL = new Profile(148.0, 0.30, 0.92, 10.0, 0.10);
    private static final Profile VIVID = new Profile(158.0, 0.40, 0.86, 16.0, 0.18);

    public static int[] processPixels(int[] pixels, ImageProcessingMode mode) {
        if (pixels == null || pixels.length == 0) {
            return pixels;
        }
        if (mode == null || mode == ImageProcessingMode.ORIGINAL) {
            int[] result = new int[pixels.length];
            for (int i = 0; i < pixels.length; i++) {
                int p = pixels[i], alpha = (p >>> 24) & 255;
                int r = (int) Math.round(compositeOnWhite((p >>> 16) & 255, alpha));
                int g = (int) Math.round(compositeOnWhite((p >>> 8) & 255, alpha));
                int b = (int) Math.round(compositeOnWhite(p & 255, alpha));
                result[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
            return result;
        }
        Profile profile = (mode == ImageProcessingMode.VIVID) ? VIVID : NATURAL;
        int[] histogram = new int[256];
        for (int pixel : pixels) {
            int alpha = (pixel >>> 24) & 0xFF;
            double r = compositeOnWhite((pixel >> 16) & 0xFF, alpha);
            double g = compositeOnWhite((pixel >> 8) & 0xFF, alpha);
            double b = compositeOnWhite(pixel & 0xFF, alpha);
            int lumaVal = (int) Math.round(luma(r, g, b));
            if (lumaVal < 0) lumaVal = 0;
            if (lumaVal > 255) lumaVal = 255;
            histogram[lumaVal]++;
        }

        double median = Math.max(1.0, percentile(histogram, pixels.length, 0.50));
        double requestedEv = Math.log(profile.targetMedian / median) / Math.log(2.0);
        // 高调图片保持原曝光，预处理只负责补亮，避免白底和透明 PNG 被反向压暗
        double clampedEv = Math.max(0.0, Math.min(profile.maxExposureEv, requestedEv));
        double exposure = Math.pow(2.0, clampedEv);

        int[] result = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            result[i] = transform(pixels[i], exposure, profile);
        }
        return result;
    }

    public static BufferedImage processImage(BufferedImage input, ImageProcessingMode mode) {
        if (input == null) return null;
        int width = input.getWidth();
        int height = input.getHeight();
        int[] pixels = new int[width * height];
        input.getRGB(0, 0, width, height, pixels, 0, width);
        int[] processed = processPixels(pixels, mode);
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        output.setRGB(0, 0, width, height, processed, 0, width);
        return output;
    }

    private static int transform(int pixel, double exposure, Profile profile) {
        int alpha = (pixel >>> 24) & 0xFF;
        double r = tone(compositeOnWhite((pixel >> 16) & 0xFF, alpha), exposure, profile);
        double g = tone(compositeOnWhite((pixel >> 8) & 0xFF, alpha), exposure, profile);
        double b = tone(compositeOnWhite(pixel & 0xFF, alpha), exposure, profile);

        double y = luma(r, g, b);
        double maxChannel = Math.max(r, Math.max(g, b));
        double minChannel = Math.min(r, Math.min(g, b));
        double saturation = (maxChannel <= 0.0) ? 0.0 : (maxChannel - minChannel) / maxChannel;
        double skinProtection = isSkinTone(r, g, b) ? 0.45 : 1.0;
        double gain = 1.0 + profile.vibrance * (1.0 - saturation) * skinProtection;

        r = Math.max(0.0, Math.min(255.0, y + (r - y) * gain));
        g = Math.max(0.0, Math.min(255.0, y + (g - y) * gain));
        b = Math.max(0.0, Math.min(255.0, y + (b - y) * gain));

        int ir = (int) Math.round(r);
        int ig = (int) Math.round(g);
        int ib = (int) Math.round(b);
        if (ir < 0) ir = 0; else if (ir > 255) ir = 255;
        if (ig < 0) ig = 0; else if (ig > 255) ig = 255;
        if (ib < 0) ib = 0; else if (ib > 255) ib = 255;

        return (0xFF << 24) | (ir << 16) | (ig << 8) | ib;
    }

    private static double tone(double channel, double exposure, Profile profile) {
        double value = Math.max(0.0, Math.min(255.0, channel * exposure));
        value = 255.0 * Math.pow(value / 255.0, profile.gamma);
        double shadowWeight = Math.max(0.0, Math.min(1.0, 1.0 - value / 180.0))
            * Math.max(0.0, Math.min(1.0, value / 32.0));
        value += profile.shadowLift * shadowWeight;
        return softHighlight(value);
    }

    /** 220 以上使用平滑软肩，避免提亮后高光直接截断。 */
    private static double softHighlight(double value) {
        if (value <= 220.0) return Math.max(0.0, value);
        double normalized = Math.max(0.0, (value - 220.0) / 35.0);
        double shoulder = (1.0 - Math.exp(-normalized)) / (1.0 - Math.exp(-1.0));
        return Math.max(0.0, Math.min(255.0, 220.0 + 35.0 * shoulder));
    }

    private static double percentile(int[] histogram, int total, double ratio) {
        int target = Math.max(1, (int) (total * ratio));
        int cumulative = 0;
        for (int i = 0; i < histogram.length; i++) {
            cumulative += histogram[i];
            if (cumulative >= target) {
                return (double) i;
            }
        }
        return 255.0;
    }

    private static double compositeOnWhite(int channel, int alpha) {
        return (channel * alpha + 255.0 * (255 - alpha)) / 255.0;
    }

    private static double luma(double r, double g, double b) {
        return 0.299 * r + 0.587 * g + 0.114 * b;
    }

    /** 肤色保护，避免鲜艳模式把肤色过度推红或推黄 */
    private static boolean isSkinTone(double r, double g, double b) {
        return r > g && g > b
            && (r - b) > 25.0
            && (r - g) >= 8.0 && (r - g) <= 85.0
            && (g - b) >= 5.0 && (g - b) <= 70.0;
    }
}
