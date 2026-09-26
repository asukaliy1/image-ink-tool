package com.mopai.toolkit.image.dither.demo;

import com.mopai.toolkit.image.dither.DitherConfig;
import com.mopai.toolkit.image.dither.DitherProcessor;
import com.mopai.toolkit.image.dither.DitherType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 基础抖动演示
 * <p>
 * 生成一张渐变测试图，分别使用各种算法进行灰度抖动，并保存到临时目录。
 */
public class BasicDitherDemo {

    /**
     * 创建一张水平灰度渐变测试图
     */
    private static BufferedImage createGradientImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = image.createGraphics();
        for (int x = 0; x < width; x++) {
            int gray = (int) (255.0 * x / width);
            g2d.setColor(new Color(gray, gray, gray));
            g2d.drawLine(x, 0, x, height);
        }
        g2d.dispose();
        return image;
    }

    public static void main(String[] args) throws IOException {
        // 输出目录
        File outputDir = new File(System.getProperty("java.io.tmpdir"), "image-toolkit-demos");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outputDir.getAbsolutePath());
        }

        // 创建测试图
        BufferedImage input = createGradientImage(400, 200);
        File originalFile = new File(outputDir, "basic_original.png");
        ImageIO.write(input, "png", originalFile);
        System.out.println("原始图已保存: " + originalFile.getAbsolutePath());

        // 遍历所有支持灰度的算法
        DitherType[] algorithms = {
            DitherType.FLOYD_STEINBERG,
            DitherType.ORDERED,
            DitherType.ATKINSON,
            DitherType.STUCKI,
            DitherType.BURKES
        };

        DitherProcessor processor = new DitherProcessor();
        for (DitherType type : algorithms) {
            DitherConfig config = new DitherConfig();
            config.setAlgorithm(type);
            config.setThreshold(128);
            config.setDitherStrength(1.0);
            config.setOutputFormat("png");
            config.applyDefaults();

            BufferedImage output = processor.process(input, config);

            File outputFile = new File(outputDir, "basic_" + type.name().toLowerCase() + ".png");
            ImageIO.write(output, "png", outputFile);
            System.out.println(type.getDisplayName() + " 已保存: " + outputFile.getAbsolutePath());
        }

        System.out.println("\n所有演示结果已保存到: " + outputDir.getAbsolutePath());
    }
}
