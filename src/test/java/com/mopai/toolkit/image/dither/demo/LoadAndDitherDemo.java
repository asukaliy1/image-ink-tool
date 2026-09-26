package com.mopai.toolkit.image.dither.demo;

import com.mopai.toolkit.image.dither.DitherConfig;
import com.mopai.toolkit.image.dither.DitherProcessor;
import com.mopai.toolkit.image.dither.DitherType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 从文件加载图片并抖动的演示
 * <p>
 * 用法：传入本地图片路径，程序会按目标宽度等比缩放并输出抖动后的图片。
 * 未传路径时，自动生成测试图作为输入。
 */
public class LoadAndDitherDemo {

    public static void main(String[] args) throws IOException {
        File outputDir = new File(System.getProperty("java.io.tmpdir"), "image-toolkit-demos");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("无法创建输出目录: " + outputDir.getAbsolutePath());
        }

        BufferedImage input;
        String sourceName;
        if (args.length > 0 && new File(args[0]).exists()) {
            File inputFile = new File(args[0]);
            input = ImageIO.read(inputFile);
            sourceName = inputFile.getName();
            System.out.println("已从文件加载图片: " + inputFile.getAbsolutePath());
        } else {
            input = createGradientImage(400, 300);
            sourceName = "gradient";
            System.out.println("未提供有效图片路径，使用内置渐变测试图");
        }

        if (input == null) {
            throw new IOException("无法获取输入图片");
        }

        System.out.println("输入尺寸: " + input.getWidth() + "x" + input.getHeight());

        // 按目标宽度 300 等比缩放，再 Floyd-Steinberg 抖动
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setTargetWidth(300);
        config.setThreshold(128);
        config.setOutputFormat("png");
        config.applyDefaults();

        DitherProcessor processor = new DitherProcessor();
        BufferedImage output = processor.process(input, config);

        String baseName = sourceName.lastIndexOf('.') > 0
            ? sourceName.substring(0, sourceName.lastIndexOf('.'))
            : sourceName;
        File outputFile = new File(outputDir, baseName + "_dithered.png");
        ImageIO.write(output, "png", outputFile);

        System.out.println("输出尺寸: " + output.getWidth() + "x" + output.getHeight());
        System.out.println("结果已保存: " + outputFile.getAbsolutePath());
    }

    private static BufferedImage createGradientImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int gray = (int) (255.0 * x / width);
                image.setRGB(x, y, (gray << 16) | (gray << 8) | gray);
            }
        }
        return image;
    }
}
