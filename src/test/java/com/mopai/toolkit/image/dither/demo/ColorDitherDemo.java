package com.mopai.toolkit.image.dither.demo;

import com.mopai.toolkit.image.dither.ColorPalette;
import com.mopai.toolkit.image.dither.DitherConfig;
import com.mopai.toolkit.image.dither.DitherProcessor;
import com.mopai.toolkit.image.dither.DitherType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 彩色抖动演示
 * <p>
 * 生成一张彩色测试图，使用 6 色电子墨水屏调色板进行量化 + 误差扩散，并保存结果。
 */
public class ColorDitherDemo {

    /**
     * 创建一张彩色渐变测试图
     */
    private static BufferedImage createColorImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (int) (255.0 * x / (width - 1));
                int g = (int) (255.0 * y / (height - 1));
                int b = (int) (255.0 * (x + y) / (width + height - 2));
                image.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }
        return image;
    }

    public static void main(String[] args) throws IOException {
        File outputDir = new File(System.getProperty("java.io.tmpdir"), "image-toolkit-demos");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outputDir.getAbsolutePath());
        }

        BufferedImage input = createColorImage(300, 300);
        File originalFile = new File(outputDir, "color_original.png");
        ImageIO.write(input, "png", originalFile);
        System.out.println("原始彩色图已保存: " + originalFile.getAbsolutePath());

        // 使用 Floyd-Steinberg 彩色模式
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setDitherMode(DitherType.DitherMode.COLOR);
        config.setColorPalettes(
            ColorPalette.eInk6QuantizeColors("133E6"),
            ColorPalette.eInk6PreviewColors("133E6")
        );
        config.applyDefaults();

        DitherProcessor processor = new DitherProcessor();
        BufferedImage output = processor.process(input, config);

        File outputFile = new File(outputDir, "color_floyd_133e6.png");
        ImageIO.write(output, "png", outputFile);
        System.out.println("彩色抖动结果已保存: " + outputFile.getAbsolutePath());

        // 验证：输出像素必须严格落在预览调色板内
        ColorPalette previewPalette = config.getEffectivePreviewPalette();
        boolean allInPalette = true;
        for (int y = 0; y < output.getHeight() && allInPalette; y++) {
            for (int x = 0; x < output.getWidth() && allInPalette; x++) {
                int rgb = output.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (previewPalette.findExactIndex(r, g, b) < 0) {
                    allInPalette = false;
                }
            }
        }
        System.out.println("所有输出像素均在调色板内: " + allInPalette);
        System.out.println("演示结果目录: " + outputDir.getAbsolutePath());
    }
}
