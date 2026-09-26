package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;

/**
 * Ordered Dithering（有序抖动）
 * 基于 4x4 Bayer 矩阵的抖动算法
 * 速度最快，适合实时预览
 */
public class OrderedDither implements DitherAlgorithm {

    /**
     * 4x4 Bayer 矩阵
     * 归一化到 0-15
     */
    private static final int[][] BAYER_4X4 = {
        { 0,  8,  2, 10},
        {12,  4, 14,  6},
        { 3, 11,  1,  9},
        {15,  7, 13,  5}
    };

    @Override
    public BufferedImage apply(BufferedImage grayscaleInput, DitherConfig config) {
        int width = grayscaleInput.getWidth();
        int height = grayscaleInput.getHeight();

        // 创建输出图像（黑白二值）
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        // 计算阈值调整因子（基于抖动强度）
        double strengthFactor = config.getDitherStrength();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                // 获取灰度值
                int gray = grayscaleInput.getRGB(x, y) & 0xFF;

                // 获取 Bayer 矩阵值（0-15）
                int bayerValue = BAYER_4X4[y % 4][x % 4];

                // 计算阈值：基础阈值 + Bayer 调整
                // Bayer 值归一化到 -8 到 +8 的范围，然后乘以强度因子
                int threshold = config.getThreshold() + (int)((bayerValue - 7.5) * 16 * strengthFactor);
                threshold = Math.max(0, Math.min(255, threshold));

                // 二值化
                int newPixel = gray > threshold ? 255 : 0;
                output.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);
            }
        }

        return output;
    }
}
