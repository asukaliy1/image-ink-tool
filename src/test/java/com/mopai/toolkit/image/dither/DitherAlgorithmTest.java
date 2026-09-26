package com.mopai.toolkit.image.dither;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 抖动算法单元测试
 */
public class DitherAlgorithmTest {

    /**
     * 创建测试图像（渐变灰度图）
     */
    private BufferedImage createTestImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g2d = image.createGraphics();

        // 创建从黑到白的渐变
        for (int x = 0; x < width; x++) {
            int gray = (int) (255.0 * x / width);
            g2d.setColor(new Color(gray, gray, gray));
            g2d.drawLine(x, 0, x, height);
        }

        g2d.dispose();
        return image;
    }

    @Test
    public void testFloydSteinbergDither() {
        BufferedImage input = createTestImage(100, 100);
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setThreshold(128);
        config.applyDefaults();

        DitherAlgorithm algorithm = new FloydSteinbergDither();
        BufferedImage output = algorithm.apply(input, config);

        assertNotNull(output);
        assertEquals(input.getWidth(), output.getWidth());
        assertEquals(input.getHeight(), output.getHeight());

        // 验证输出是黑白二值图
        boolean hasBlack = false;
        boolean hasWhite = false;
        for (int y = 0; y < output.getHeight() && (!hasBlack || !hasWhite); y++) {
            for (int x = 0; x < output.getWidth() && (!hasBlack || !hasWhite); x++) {
                int pixel = output.getRGB(x, y) & 0xFF;
                if (pixel == 0) hasBlack = true;
                if (pixel == 255) hasWhite = true;
            }
        }
        assertTrue(hasBlack, "输出应包含黑色像素");
        assertTrue(hasWhite, "输出应包含白色像素");
    }

    @Test
    public void testFloydSteinbergColorOutputUsesPaletteOnly() {
        BufferedImage input = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = input.createGraphics();
        for (int y = 0; y < input.getHeight(); y++) {
            for (int x = 0; x < input.getWidth(); x++) {
                int r = (int) (255.0 * x / (input.getWidth() - 1));
                int g = (int) (255.0 * y / (input.getHeight() - 1));
                int b = (int) (255.0 * (x + y) / (input.getWidth() + input.getHeight() - 2));
                input.setRGB(x, y, new Color(r, g, b).getRGB());
            }
        }
        g2d.dispose();

        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setDitherMode(DitherType.DitherMode.COLOR);
        config.setColorPalettes(
            ColorPalette.eInk6QuantizeColors("133E6"),
            ColorPalette.eInk6PreviewColors("133E6")
        );
        config.applyDefaults();

        BufferedImage output = new FloydSteinbergDither().apply(input, config);
        ColorPalette palette = config.getEffectivePreviewPalette();

        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                int rgb = output.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                assertTrue(palette.findExactIndex(r, g, b) >= 0, "彩色输出必须严格落在调色板内");
            }
        }
    }

    @Test
    public void testColorProcessorFlattensTransparentPixelsToWhite() throws IOException {
        BufferedImage input = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = input.createGraphics();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.fillRect(0, 0, input.getWidth(), input.getHeight());
        g2d.dispose();

        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setDitherMode(DitherType.DitherMode.COLOR);
        config.setColorPalettes(
            ColorPalette.eInk6QuantizeColors(),
            ColorPalette.eInk6PreviewColors()
        );
        config.applyDefaults();

        BufferedImage output = new DitherProcessor().process(input, config);
        ColorPalette previewPalette = config.getEffectivePreviewPalette();
        int expectedWhite = (previewPalette.getR(1) << 16) | (previewPalette.getG(1) << 8) | previewPalette.getB(1);

        for (int y = 0; y < output.getHeight(); y++) {
            for (int x = 0; x < output.getWidth(); x++) {
                int rgb = output.getRGB(x, y);
                assertEquals(expectedWhite, rgb & 0xFFFFFF, "透明区域应按白底处理");
            }
        }
    }

    @Test
    public void testOrderedDither() {
        BufferedImage input = createTestImage(100, 100);
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.ORDERED);
        config.setThreshold(128);
        config.applyDefaults();

        DitherAlgorithm algorithm = new OrderedDither();
        BufferedImage output = algorithm.apply(input, config);

        assertNotNull(output);
        assertEquals(input.getWidth(), output.getWidth());
        assertEquals(input.getHeight(), output.getHeight());
    }

    @Test
    public void testAtkinsonDither() {
        BufferedImage input = createTestImage(100, 100);
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.ATKINSON);
        config.setThreshold(128);
        config.applyDefaults();

        DitherAlgorithm algorithm = new AtkinsonDither();
        BufferedImage output = algorithm.apply(input, config);

        assertNotNull(output);
        assertEquals(input.getWidth(), output.getWidth());
        assertEquals(input.getHeight(), output.getHeight());
    }

    @Test
    public void testAllAlgorithms() throws IOException {
        BufferedImage input = createTestImage(200, 200);

        for (DitherType type : DitherType.values()) {
            // GRAY16 目前在 DitherProcessor 中未注册实现，跳过
            if (type == DitherType.GRAY16) {
                continue;
            }

            DitherConfig config = new DitherConfig();
            config.setAlgorithm(type);
            config.setThreshold(128);
            config.applyDefaults();

            DitherProcessor processor = new DitherProcessor();
            BufferedImage output = processor.process(input, config);

            assertNotNull(output, type.name() + " 算法应返回非空结果");
            assertEquals(input.getWidth(), output.getWidth(), type.name() + " 宽度应一致");
            assertEquals(input.getHeight(), output.getHeight(), type.name() + " 高度应一致");
        }
    }

    @Test
    public void testDitherConfigValidation() {
        DitherConfig config = new DitherConfig();

        // 测试缺少算法类型
        assertThrows(IllegalArgumentException.class, config::validate);

        // 测试无效阈值
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.setThreshold(300);
        assertThrows(IllegalArgumentException.class, config::validate);

        // 测试无效抖动强度
        config.setThreshold(128);
        config.setDitherStrength(1.5);
        assertThrows(IllegalArgumentException.class, config::validate);

        // 测试有效配置
        config.setDitherStrength(1.0);
        assertDoesNotThrow(config::validate);
    }

    @Test
    public void testDitherConfigDefaults() {
        DitherConfig config = new DitherConfig();
        config.setAlgorithm(DitherType.FLOYD_STEINBERG);
        config.applyDefaults();

        assertEquals(128, config.getThreshold());
        assertEquals(1.0, config.getDitherStrength());
        assertEquals("png", config.getOutputFormat());
        assertEquals(false, config.getAddGrain());
        assertEquals(5, config.getGrainIntensity());
    }
}
