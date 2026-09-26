package com.mopai.toolkit.image.dither.demo;

import com.mopai.toolkit.image.dither.BmpToBinConverter;
import com.mopai.toolkit.image.dither.BmpToBinConverter.BinColorDepth;
import com.mopai.toolkit.image.dither.BmpToBinConverter.BinConvertResult;
import com.mopai.toolkit.image.dither.ColorPalette;
import com.mopai.toolkit.image.dither.DitherConfig;
import com.mopai.toolkit.image.dither.DitherProcessor;
import com.mopai.toolkit.image.dither.DitherType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * BIN 转换完整流程演示
 * <p>
 * 1. 创建彩色测试图
 * 2. 使用彩色抖动 + 调色板量化
 * 3. 将抖动结果转换为 INDEX6 BIN 格式
 * 4. 保存 BMP 预览图和 BIN 文件
 */
public class BinConversionDemo {

    private static BufferedImage createColorImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = (int) (255.0 * x / (width - 1));
                int g = (int) (255.0 * y / (height - 1));
                int b = (int) (255.0 * (width - 1 - x) / (width - 1));
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

        // 1. 创建 800x480 的彩色测试图（模拟 7.3 寸相册分辨率）
        BufferedImage input = createColorImage(800, 480);
        File originalFile = new File(outputDir, "bin_original.png");
        ImageIO.write(input, "png", originalFile);
        System.out.println("原始图已保存: " + originalFile.getAbsolutePath());

        // 2. 配置彩色抖动
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setDitherMode(DitherType.DitherMode.COLOR);
        config.setColorPalettes(
            ColorPalette.eInk6QuantizeColors("073E6"),
            ColorPalette.eInk6PreviewColors("073E6")
        );
        config.applyDefaults();

        // 3. 执行抖动
        DitherProcessor processor = new DitherProcessor();
        BufferedImage dithered = processor.process(input, config);
        File previewFile = new File(outputDir, "bin_preview_073e6.bmp");
        ImageIO.write(dithered, "bmp", previewFile);
        System.out.println("BMP 预览图已保存: " + previewFile.getAbsolutePath());

        // 4. 转换为 INDEX6 BIN
        ColorPalette previewPalette = config.getEffectivePreviewPalette();
        BinConvertResult result = BmpToBinConverter.convert(dithered, previewPalette, BinColorDepth.INDEX6);

        // 5. 保存 BIN 文件
        File binFile = new File(outputDir, "bin_output_073e6.bin");
        try (FileOutputStream fos = new FileOutputStream(binFile)) {
            fos.write(result.binData());
        }

        System.out.println("BIN 文件已保存: " + binFile.getAbsolutePath());
        System.out.println("分辨率: " + result.width() + "x" + result.height());
        System.out.println("色深: " + result.colorDepth());
        System.out.println("文件大小: " + result.binData().length + " 字节");
        System.out.println("演示结果目录: " + outputDir.getAbsolutePath());
    }
}
