package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Stucki 抖动算法
 * 扩散范围最大的误差扩散算法
 * 产生最平滑的过渡效果
 *
 * 误差分配矩阵：
 *            X   8/42  4/42
 *  2/42  4/42  8/42  4/42  2/42
 *  1/42  2/42  4/42  2/42  1/42
 *
 * 支持 Z 字形扫描（serpentine）以减少水平条纹
 */
public class StuckiDither implements DitherAlgorithm {

    @Override
    public BufferedImage apply(BufferedImage grayscaleInput, DitherConfig config) {
        int width = grayscaleInput.getWidth();
        int height = grayscaleInput.getHeight();
        boolean serpentine = config.getSerpentine() != null && config.getSerpentine();

        // 创建输出图像（黑白二值）
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);

        // 误差缓冲区（需要当前行和后两行）
        int[][] errors = new int[3][width];

        for (int y = 0; y < height; y++) {
            int row0 = y % 3;
            int row1 = (y + 1) % 3;
            int row2 = (y + 2) % 3;
            boolean forward = !serpentine || (y % 2 == 0);

            int startX = forward ? 0 : width - 1;
            int endX = forward ? width : -1;
            int stepX = forward ? 1 : -1;

            for (int x = startX; x != endX; x += stepX) {
                // 获取原始灰度值 + 累积误差
                int gray = (grayscaleInput.getRGB(x, y) & 0xFF) + errors[row0][x];
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
                if (x1 >= 0 && x1 < width) errors[row0][x1] += error * 8 / 42;
                if (x2 >= 0 && x2 < width) errors[row0][x2] += error * 4 / 42;

                // 下一行扩散
                if (y + 1 < height) {
                    int xm2 = x - stepX * 2;
                    int xm1 = x - stepX;
                    int xp1 = x + stepX;
                    int xp2 = x + stepX * 2;

                    if (xm2 >= 0 && xm2 < width) errors[row1][xm2] += error * 2 / 42;
                    if (xm1 >= 0 && xm1 < width) errors[row1][xm1] += error * 4 / 42;
                    errors[row1][x] += error * 8 / 42;
                    if (xp1 >= 0 && xp1 < width) errors[row1][xp1] += error * 4 / 42;
                    if (xp2 >= 0 && xp2 < width) errors[row1][xp2] += error * 2 / 42;
                }

                // 下下一行扩散
                if (y + 2 < height) {
                    int xm2 = x - stepX * 2;
                    int xm1 = x - stepX;
                    int xp1 = x + stepX;
                    int xp2 = x + stepX * 2;

                    if (xm2 >= 0 && xm2 < width) errors[row2][xm2] += error * 1 / 42;
                    if (xm1 >= 0 && xm1 < width) errors[row2][xm1] += error * 2 / 42;
                    errors[row2][x] += error * 4 / 42;
                    if (xp1 >= 0 && xp1 < width) errors[row2][xp1] += error * 2 / 42;
                    if (xp2 >= 0 && xp2 < width) errors[row2][xp2] += error * 1 / 42;
                }
            }

            // 清空当前行误差
            Arrays.fill(errors[row0], 0);
        }

        return output;
    }
}
