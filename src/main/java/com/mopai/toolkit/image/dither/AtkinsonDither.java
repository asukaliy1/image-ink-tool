package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Atkinson 抖动算法
 * Bill Atkinson 为早期 Macintosh 开发的算法
 * 误差扩散范围更广，产生高对比度和明亮效果
 * 特别适合墨水屏设备
 *
 * 误差分配矩阵（每个误差的 1/8）：
 *       X   1/8  1/8
 *  1/8  1/8  1/8
 *       1/8
 *
 * 支持 Z 字形扫描（serpentine）以减少水平条纹
 * 支持灰度和彩色（调色板量化）两种模式
 */
public class AtkinsonDither implements DitherAlgorithm {

    @Override
    public BufferedImage apply(BufferedImage input, DitherConfig config) {
        if (config.getDitherMode() == DitherType.DitherMode.COLOR) {
            return applyColor(input, config);
        }
        return applyGrayscale(input, config);
    }

    private BufferedImage applyGrayscale(BufferedImage input, DitherConfig config) {
        int width = input.getWidth();
        int height = input.getHeight();
        boolean serpentine = config.getSerpentine() != null && config.getSerpentine();

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
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
                int gray = (input.getRGB(x, y) & 0xFF) + errors[row0][x];
                gray = Math.max(0, Math.min(255, gray));

                int newPixel = gray >= config.getThreshold() ? 255 : 0;
                output.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);

                int error = (int) ((gray - newPixel) * config.getDitherStrength() / 8);

                if (x + stepX >= 0 && x + stepX < width) {
                    errors[row0][x + stepX] += error;
                }
                if (x + stepX * 2 >= 0 && x + stepX * 2 < width) {
                    errors[row0][x + stepX * 2] += error;
                }
                if (y + 1 < height) {
                    if (x - stepX >= 0 && x - stepX < width) {
                        errors[row1][x - stepX] += error;
                    }
                    errors[row1][x] += error;
                    if (x + stepX >= 0 && x + stepX < width) {
                        errors[row1][x + stepX] += error;
                    }
                }
                if (y + 2 < height) {
                    errors[row2][x] += error;
                }
            }
            Arrays.fill(errors[row0], 0);
        }
        return output;
    }

    private BufferedImage applyColor(BufferedImage input, DitherConfig config) {
        int width = input.getWidth();
        int height = input.getHeight();
        boolean serpentine = config.getSerpentine() != null && config.getSerpentine();
        ColorPalette quantizePalette = config.getEffectiveQuantizePalette();
        ColorPalette previewPalette = config.getEffectivePreviewPalette();

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int[][][] errors = new int[3][3][width];

        for (int y = 0; y < height; y++) {
            int row0 = y % 3;
            int row1 = (y + 1) % 3;
            int row2 = (y + 2) % 3;
            boolean forward = !serpentine || (y % 2 == 0);

            int startX = forward ? 0 : width - 1;
            int endX = forward ? width : -1;
            int stepX = forward ? 1 : -1;

            for (int x = startX; x != endX; x += stepX) {
                int rgb = input.getRGB(x, y);
                int pr = Math.max(0, Math.min(255, ((rgb >> 16) & 0xFF) + errors[0][row0][x]));
                int pg = Math.max(0, Math.min(255, ((rgb >> 8) & 0xFF) + errors[1][row0][x]));
                int pb = Math.max(0, Math.min(255, (rgb & 0xFF) + errors[2][row0][x]));

                int nearestIdx = quantizePalette.findNearestIndex(pr, pg, pb);
                int qr = quantizePalette.getR(nearestIdx);
                int qg = quantizePalette.getG(nearestIdx);
                int qb = quantizePalette.getB(nearestIdx);
                int previewR = previewPalette.getR(nearestIdx);
                int previewG = previewPalette.getG(nearestIdx);
                int previewB = previewPalette.getB(nearestIdx);
                output.setRGB(x, y, (previewR << 16) | (previewG << 8) | previewB);

                double strength = config.getDitherStrength() / 8.0;
                int errR = (int) ((pr - qr) * strength);
                int errG = (int) ((pg - qg) * strength);
                int errB = (int) ((pb - qb) * strength);

                // 当前行扩散
                if (x + stepX >= 0 && x + stepX < width) {
                    errors[0][row0][x + stepX] += errR;
                    errors[1][row0][x + stepX] += errG;
                    errors[2][row0][x + stepX] += errB;
                }
                if (x + stepX * 2 >= 0 && x + stepX * 2 < width) {
                    errors[0][row0][x + stepX * 2] += errR;
                    errors[1][row0][x + stepX * 2] += errG;
                    errors[2][row0][x + stepX * 2] += errB;
                }

                // 下一行扩散
                if (y + 1 < height) {
                    if (x - stepX >= 0 && x - stepX < width) {
                        errors[0][row1][x - stepX] += errR;
                        errors[1][row1][x - stepX] += errG;
                        errors[2][row1][x - stepX] += errB;
                    }
                    errors[0][row1][x] += errR;
                    errors[1][row1][x] += errG;
                    errors[2][row1][x] += errB;
                    if (x + stepX >= 0 && x + stepX < width) {
                        errors[0][row1][x + stepX] += errR;
                        errors[1][row1][x + stepX] += errG;
                        errors[2][row1][x + stepX] += errB;
                    }
                }

                // 下下一行扩散
                if (y + 2 < height) {
                    errors[0][row2][x] += errR;
                    errors[1][row2][x] += errG;
                    errors[2][row2][x] += errB;
                }
            }
            for (int ch = 0; ch < 3; ch++) {
                Arrays.fill(errors[ch][row0], 0);
            }
        }
        return output;
    }
}
