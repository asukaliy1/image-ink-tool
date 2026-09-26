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
 * 简单案例：彩色抖动 → 生成 BIN 文件
 * <p>
 * 1. 读取本地图片（未提供路径时使用内置彩色测试图）
 * 2. 使用 6 色电子墨水屏调色板进行彩色抖动
 * 3. 将结果转换为 INDEX6 BIN 格式
 * 4. 输出 BMP 预览图和 BIN 文件
 */
public class SimpleColorDitherToBinDemo {

    public static void main(String[] args) throws IOException {
        // 输出目录
        File outputDir = new File(System.getProperty("java.io.tmpdir"), "image-toolkit-demos");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outputDir.getAbsolutePath());
        }

        // 1. 读取图片
        BufferedImage input;
        String name;
        if (args.length > 0 && new File(args[0]).exists()) {
            File inputFile = new File(args[0]);
            input = ImageIO.read(inputFile);
            name = inputFile.getName();
            System.out.println("读取图片: " + inputFile.getAbsolutePath());
        } else {
            input = createColorImage(800, 480);
            name = "demo";
            System.out.println("未提供图片路径，使用内置 800x480 彩色测试图");
        }
        System.out.println("输入尺寸: " + input.getWidth() + "x" + input.getHeight());

        // 2. 配置彩色抖动
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setDitherMode(DitherType.DitherMode.COLOR);
        config.setColorPalettes(
            ColorPalette.eInk6QuantizeColors("133E6"),  // 量化调色板：用于误差计算
            ColorPalette.eInk6PreviewColors("133E6")    // 预览调色板：用于输出和 BIN 精确匹配
        );
        config.applyDefaults();

        // 3. 执行抖动
        DitherProcessor processor = new DitherProcessor();
        BufferedImage dithered = processor.process(input, config);

        // 4. 保存 BMP 预览图
        String baseName = name.lastIndexOf('.') > 0
            ? name.substring(0, name.lastIndexOf('.'))
            : name;
        File previewFile = new File(outputDir, baseName + "_preview.bmp");
        ImageIO.write(dithered, "bmp", previewFile);
        System.out.println("预览图: " + previewFile.getAbsolutePath());

        // 5. 转换为 INDEX6 BIN
        ColorPalette previewPalette = config.getEffectivePreviewPalette();
        BinConvertResult result = BmpToBinConverter.convert(dithered, previewPalette, BinColorDepth.INDEX6);

        // 6. 保存 BIN 文件
        File binFile = new File(outputDir, baseName + "_6color.bin");
        try (FileOutputStream fos = new FileOutputStream(binFile)) {
            fos.write(result.binData());
        }

        System.out.println("BIN 文件: " + binFile.getAbsolutePath());
        System.out.println("分辨率: " + result.width() + "x" + result.height());
        System.out.println("BIN 大小: " + result.binData().length + " 字节");
    }

    /**
     * 内置彩色测试图
     */
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
}
