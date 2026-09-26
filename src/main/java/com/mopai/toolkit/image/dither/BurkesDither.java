package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Burkes 抖动算法
 * Floyd-Steinberg 和 Stucki 的折中方案
 * 平衡性能和质量
 *
 * 误差分配矩阵：
 *            X   8/32  4/32
 *  2/32  4/32  8/32  4/32  2/32
 *
 * 支持 Z 字形扫描（serpentine）以减少水平条纹
 */
public class BurkesDither implements DitherAlgorithm {

    @Override
    public BufferedImage apply(BufferedImage grayscaleInput, DitherConfig config) {
        int width = grayscaleInput.getWidth();
        int height = grayscaleInput.getHeight();
        boolean serpentine = config.getSerpentine() != null && config.getSerpentine();

        // 创建输出图像（黑白二值）
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        // 误差缓冲区（需要当前行和下一行）
        int[][] errors = new int[2][width];

        for (int y = 0; y < height; y++) {
            int currentRow = y % 2;
            int nextRow = (y + 1) % 2;
            boolean forward = !serpentine || (y % 2 == 0);

            int startX = forward ? 0 : width - 1;
            int endX = forward ? width : -1;
            int stepX = forward ? 1 : -1;

            for (int x = startX; x != endX; x += stepX) {
                // 获取原始灰度值 + 累积误差
                int gray = (grayscaleInput.getRGB(x, y) & 0xFF) + errors[currentRow][x];
                gray = Math.max(0, Math.min(255, gray));

                // 二值化
                int newPixel = gray >= config.getThreshold() ? 255 : 0;
                output.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);

                // 计算误差
                int error = (gray - newPixel);
                error = (int)(error * config.getDitherStrength());

                // 当前行扩散
                int x1 = x + stepX;
                int x2 = x + stepX * 2;
                if (x1 >= 0 && x1 < width) errors[currentRow][x1] += error * 8 / 32;
                if (x2 >= 0 && x2 < width) errors[currentRow][x2] += error * 4 / 32;

                // 下一行扩散
                if (y + 1 < height) {
                    int xm2 = x - stepX * 2;
                    int xm1 = x - stepX;
                    int xp1 = x + stepX;
                    int xp2 = x + stepX * 2;

                    if (xm2 >= 0 && xm2 < width) errors[nextRow][xm2] += error * 2 / 32;
                    if (xm1 >= 0 && xm1 < width) errors[nextRow][xm1] += error * 4 / 32;
                    errors[nextRow][x] += error * 8 / 32;
                    if (xp1 >= 0 && xp1 < width) errors[nextRow][xp1] += error * 4 / 32;
                    if (xp2 >= 0 && xp2 < width) errors[nextRow][xp2] += error * 2 / 32;
                }
            }

            // 清空当前行误差
            Arrays.fill(errors[currentRow], 0);
        }

        return output;
    }
}
