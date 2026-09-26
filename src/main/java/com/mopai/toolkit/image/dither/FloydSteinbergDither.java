package com.mopai.toolkit.image.dither;

import java.awt.image.BufferedImage;
import java.util.Arrays;

/**
 * Floyd-Steinberg 抖动算法
 * 经典的误差扩散算法，细节保留最好
 *
 * 误差分配矩阵：
 *       X   7/16
 *  3/16 5/16 1/16
 *
 * 支持 Z 字形扫描（serpentine）以减少水平条纹
 * 支持灰度和彩色（调色板量化）两种模式
 */
public class FloydSteinbergDither implements DitherAlgorithm {

    @Override
    public BufferedImage apply(BufferedImage input, DitherConfig config) {
        if (config.getDitherMode() == DitherType.DitherMode.COLOR) {
            return applyColor(input, config);
        }
        return applyGrayscale(input, config);
    }

    /**
     * 灰度模式（原有逻辑）
     */
    private BufferedImage applyGrayscale(BufferedImage input, DitherConfig config) {
        int width = input.getWidth();
        int height = input.getHeight();
        boolean serpentine = config.getSerpentine() != null && config.getSerpentine();

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        int[][] errors = new int[2][width];

        for (int y = 0; y < height; y++) {
            int currentRow = y % 2;
            int nextRow = (y + 1) % 2;
            boolean forward = !serpentine || (y % 2 == 0);

            int startX = forward ? 0 : width - 1;
            int endX = forward ? width : -1;
            int stepX = forward ? 1 : -1;

            for (int x = startX; x != endX; x += stepX) {
                int gray = (input.getRGB(x, y) & 0xFF) + errors[currentRow][x];
                gray = Math.max(0, Math.min(255, gray));

                int newPixel = gray >= config.getThreshold() ? 255 : 0;
                output.setRGB(x, y, (newPixel << 16) | (newPixel << 8) | newPixel);

                int error = (int) ((gray - newPixel) * config.getDitherStrength());

                // Floyd-Steinberg 权重固定为 7/3/5/1，不随扫描方向变化
                // stepX 处理方向，权重始终不变
                if (x + stepX >= 0 && x + stepX < width) {
                    errors[currentRow][x + stepX] += error * 7 / 16;     // 同行，扫描方向
                }
                if (y + 1 < height) {
                    if (x - stepX >= 0 && x - stepX < width) {
                        errors[nextRow][x - stepX] += error * 3 / 16;   // 下一行，逆扫描方向
                    }
                    errors[nextRow][x] += error * 5 / 16;                 // 下一行，正下方
                    if (x + stepX >= 0 && x + stepX < width) {
                        errors[nextRow][x + stepX] += error * 1 / 16;   // 下一行，扫描方向
                    }
                }
            }
            Arrays.fill(errors[currentRow], 0);
        }
        return output;
    }

    /**
     * 先求保持目标线性 RGB 的低彩色用量混合，再扩散各墨水覆盖率误差。
     * 避免感知最近色用大量互补色凑出平均颜色，减少平坦区域的彩色颗粒。
     */
    private BufferedImage applyColor(BufferedImage input, DitherConfig config) {
        ColorPalette quantize = config.getEffectiveQuantizePalette();
        ColorPalette preview = config.getEffectivePreviewPalette();
        if (quantize == null || preview == null || quantize.size() != 6 || preview.size() != 6) {
            throw new IllegalArgumentException("六色抖动要求量化表和预览表均为六色，顺序为黑白红绿蓝黄");
        }
        int width = input.getWidth(), height = input.getHeight();
        boolean serpentine = Boolean.TRUE.equals(config.getSerpentine());
        double strength = config.getDitherStrength() == null ? 1.0 : config.getDitherStrength();
        double[][] colors = new double[6][];
        int[] outputRgb = new int[6];
        for (int i = 0; i < 6; i++) {
            colors[i] = LinearColorSpace.rgb(quantize.getR(i), quantize.getG(i), quantize.getB(i));
            outputRgb[i] = (preview.getR(i) << 16) | (preview.getG(i) << 8) | preview.getB(i);
        }
        PaletteGamut gamut = new PaletteGamut(colors);
        PaletteMixture mixture = new PaletteMixture(colors);
        BufferedImage prepared = ImagePreprocessor.processImage(input, config.getProcessingMode());
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        double[][][] errors = new double[2][width][6];
        // 缓存覆盖率而非最终墨点，平坦区域仍可连续扩散；内存不随颜色数量增长。
        int[] cacheKeys = new int[4096];
        Arrays.fill(cacheKeys, -1);
        double[][] cacheWeights = new double[4096][];
        for (int y = 0; y < height; y++) {
            int cur = y % 2, next = (y + 1) % 2;
            int step = (!serpentine || y % 2 == 0) ? 1 : -1;
            for (int x = step == 1 ? 0 : width - 1; x >= 0 && x < width; x += step) {
                int rgb = prepared.getRGB(x, y) & 0xffffff;
                int slot = (rgb * 0x9e3779b9) >>> 20;
                if (cacheKeys[slot] != rgb) {
                    cacheKeys[slot] = rgb;
                    int pr = (rgb >>> 16) & 255;
                    int pg = (rgb >>> 8) & 255;
                    int pb = rgb & 255;
                    double[] target = gamut.map(LinearColorSpace.rgb(pr, pg, pb));
                    double[] w = mixture.weights(target);
                    optimizeInkWeights(w, pr, pg, pb);
                    cacheWeights[slot] = w;
                }
                double[] weights = cacheWeights[slot];
                // 选择欠账最多的墨水；不存在的互补色不会在平坦区域凭空出现。
                int index = 0;
                double largest = Double.NEGATIVE_INFINITY;
                for (int ink = 0; ink < 6; ink++) {
                    double coverage = weights[ink] + errors[cur][x][ink];
                    if (coverage > largest) {
                        largest = coverage;
                        index = ink;
                    }
                }
                output.setRGB(x, y, outputRgb[index]);
                for (int ink = 0; ink < 6; ink++) {
                    double error = (weights[ink] + errors[cur][x][ink] - (ink == index ? 1 : 0)) * strength;
                    if (x + step >= 0 && x + step < width) errors[cur][x + step][ink] += error * 7 / 16;
                    if (y + 1 < height) {
                        if (x - step >= 0 && x - step < width) errors[next][x - step][ink] += error * 3 / 16;
                        errors[next][x][ink] += error * 5 / 16;
                        if (x + step >= 0 && x + step < width) errors[next][x + step][ink] += error / 16;
                    }
                }
            }
            for (double[] pixel : errors[cur]) Arrays.fill(pixel, 0);
        }
        return output;
    }

    /**
     * 针对电子墨水屏反射式低对比度与物理微囊吸收特性，全面抑制多余黑色成分并提升整体通透度：
     * 1. 纯色/高饱和有色区域（红、绿、蓝、黄、橙、青）：
     *    由于物理 Gamma 2.4 解码严重压缩了各通道光强，凸包算法误为纯色分配了 30%~70% 的黑墨。
     *    在此强力剔除各纯色区域中的黑色，并将权重转移给对应的纯色墨水，杜绝色块发黑发脏；
     * 2. 全图中间调提亮（解决整体偏暗）：
     *    针对 Gamma 2.4 解码在中间调（sRGB 50~200）产生的严重下凹（50% 灰产生近 80% 黑点），
     *    反向补偿中间调多余的黑墨至白墨，消除黑斑与煤渣感，使整幅画面明快通透、层次分明。
     */
    private static void optimizeInkWeights(double[] w, int pr, int pg, int pb) {
        if (w[0] <= 1e-6) return;

        int maxC = Math.max(pr, Math.max(pg, pb));
        int minC = Math.min(pr, Math.min(pg, pb));
        int chroma = maxC - minC;

        // 阶段 1：对高饱和有色区域进行黑色抑制，转移给对应的彩色墨水
        if (chroma >= 25 && maxC >= 35) {
            // 黄主导（红绿均高且显著高于蓝）：转移至黄墨水(5)
            if (pr >= 60 && pg >= 60 && Math.min(pr, pg) > pb + 30) {
                int yellowBase = Math.min(pr, pg);
                int yellowDiff = yellowBase - pb;
                double dominance = (double) yellowDiff / Math.max(1, yellowBase);
                double brightness = Math.min(1.0, (yellowBase - 35.0) / 120.0);
                double suppressRate = Math.min(1.0, dominance * 2.0) * brightness;
                if (suppressRate > 0 && w[0] > 1e-6) {
                    double transfer = w[0] * suppressRate;
                    w[0] -= transfer;
                    w[5] += transfer;
                }
            }
            // 绿主导：转移至绿墨水(3)
            else if (pg > pr && pg > pb) {
                int greenDiff = pg - Math.max(pr, pb);
                double dominance = (double) greenDiff / Math.max(1, pg);
                double brightness = Math.min(1.0, (pg - 35.0) / 120.0);
                double suppressRate = Math.min(1.0, dominance * 2.0) * brightness;
                if (suppressRate > 0 && w[0] > 1e-6) {
                    double transfer = w[0] * suppressRate;
                    w[0] -= transfer;
                    w[3] += transfer;
                }
            }
            // 红主导（纯红、鲜红、橙红）：转移至红墨水(2) 或 橙黄色(红2+黄5)
            else if (pr > pg && pr > pb) {
                int redDiff = pr - Math.max(pg, pb);
                double dominance = (double) redDiff / Math.max(1, pr);
                double brightness = Math.min(1.0, (pr - 35.0) / 120.0);
                double suppressRate = Math.min(1.0, dominance * 2.0) * brightness;
                if (suppressRate > 0 && w[0] > 1e-6) {
                    double transfer = w[0] * suppressRate;
                    if (pg > pb && (pg - pb) >= 20) {
                        double yRatio = Math.min(0.5, (double) (pg - pb) / redDiff * 0.7);
                        w[0] -= transfer;
                        w[5] += transfer * yRatio;
                        w[2] += transfer * (1.0 - yRatio);
                    } else {
                        w[0] -= transfer;
                        w[2] += transfer;
                    }
                }
            }
            // 蓝主导（纯蓝、深蓝、青蓝）：转移至蓝墨水(4) 或 青色(蓝4+绿3)
            else if (pb > pr && pb > pg) {
                int blueDiff = pb - Math.max(pr, pg);
                double dominance = (double) blueDiff / Math.max(1, pb);
                double brightness = Math.min(1.0, (pb - 35.0) / 120.0);
                double suppressRate = Math.min(1.0, dominance * 2.0) * brightness;
                if (suppressRate > 0 && w[0] > 1e-6) {
                    double transfer = w[0] * suppressRate;
                    if (pg > pr && (pg - pr) >= 20) {
                        double gRatio = Math.min(0.5, (double) (pg - pr) / blueDiff * 0.7);
                        w[0] -= transfer;
                        w[3] += transfer * gRatio;
                        w[4] += transfer * (1.0 - gRatio);
                    } else {
                        w[0] -= transfer;
                        w[4] += transfer;
                    }
                }
            }
        }

        // 阶段 2：全图中间调提亮与多余黑墨补偿（消除画面整体偏暗与煤渣感）
        // 感知亮度 s ∈ [0, 1]：Gamma 2.4 与线性感知之间的偏差曲线为 s - s^2.4，在 s ≈ 0.5 处达最大值 ~0.286
        if (w[0] > 1e-6 && maxC >= 25) {
            double s = (0.299 * pr + 0.587 * pg + 0.114 * pb) / 255.0;
            double midtoneExcess = Math.max(0.0, s - Math.pow(s, 2.4));
            double liftTransfer = Math.min(w[0], 0.70 * midtoneExcess);
            if (liftTransfer > 0) {
                w[0] -= liftTransfer;
                w[1] += liftTransfer; // 补偿给白墨水，显著提升画面通透度与明亮度
            }
        }
    }
}
