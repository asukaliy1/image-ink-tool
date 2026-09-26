package com.mopai.toolkit.image.dither;

import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ColorFidelityTest {
    private DitherConfig config() {
        DitherConfig c = new DitherConfig();
        c.setAlgorithm(DitherType.FLOYD_STEINBERG);
        c.setDitherMode(DitherType.DitherMode.COLOR);
        c.setColorPalettes(ColorPalette.eInk6QuantizeColors(), ColorPalette.eInk6PreviewColors());
        return c;
    }

    private BufferedImage solid(int r, int g, int b) {
        BufferedImage image = new BufferedImage(192, 192, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[192];
        Arrays.fill(row, (r << 16) | (g << 8) | b);
        for (int y = 0; y < 192; y++) image.setRGB(0, y, 192, 1, row, 0, 192);
        return image;
    }

    private double linear(int v) {
        double s = v / 255.0;
        return s <= 0.04045 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    @Test
    void warmColorsPreserveAverageLinearColor() {
        int[][] colors = {{180,150,130}, {210,170,150}, {230,200,180}, {160,150,100}};
        for (int[] c : colors) {
            BufferedImage out = new FloydSteinbergDither().apply(solid(c[0],c[1],c[2]), config());
            double[] mean = new double[3];
            for (int y = 0; y < out.getHeight(); y++) for (int x = 0; x < out.getWidth(); x++) {
                int rgb = out.getRGB(x,y);
                for (int k = 0; k < 3; k++) mean[k] += linear((rgb >> (16 - 8*k)) & 255) / (192*192);
            }
            // 暖色调保持红>绿>蓝的暖调次序，不偏色，且明度经墨水屏中间调提亮适应反射观察
            assertTrue(mean[0] > mean[1] && mean[1] >= mean[2],
                "warm colors must keep warm hue order: " + Arrays.toString(c) + " -> " + Arrays.toString(mean));
        }
    }

    @Test
    void grayRampStaysNeutralWithoutChangingTone() {
        double prevWhite = -1.0;
        for (int level : new int[]{0,32,64,128,192,240,255}) {
            BufferedImage out = new FloydSteinbergDither().apply(solid(level,level,level), config());
            int white = 0;
            for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
                int rgb = out.getRGB(x,y) & 0xffffff;
                assertTrue(rgb == 0 || rgb == 0xffffff, "gray must not acquire colored dots");
                if (rgb == 0xffffff) white++;
            }
            double whiteRatio = white / (192.0 * 192);
            assertTrue(whiteRatio >= prevWhite, "gray ramp white ratio must be monotonic: level=" + level);
            prevWhite = whiteRatio;
            if (level == 0) assertEquals(0.0, whiteRatio, 1e-6);
            if (level == 255) assertEquals(1.0, whiteRatio, 1e-6);
            // 墨水屏中间调 128 白墨水占比在 35%~55% 之间（避免原 Gamma 2.4 下高达 80% 煤渣黑）
            if (level == 128) {
                assertTrue(whiteRatio >= 0.35 && whiteRatio <= 0.55, "level 128 white ratio in [35%, 55%]: " + whiteRatio);
            }
        }
    }

    @Test
    void quantizationUsesMeasuredPaletteInsteadOfPreviewRgb() {
        DitherConfig c = config();
        c.setColorPalettes(new ColorPalette(Arrays.asList(
            new int[]{0,0,0}, new int[]{255,255,255}, new int[]{180,70,60},
            new int[]{60,160,70}, new int[]{50,70,170}, new int[]{210,190,60})),
            ColorPalette.eInk6PreviewColors());
        BufferedImage out = new FloydSteinbergDither().apply(solid(180,70,60), c);
        for (int y=0;y<192;y++) for (int x=0;x<192;x++)
            assertEquals(0xff0000, out.getRGB(x,y)&0xffffff);
    }

    @Test
    void exactInkColorsRemainExact() {
        ColorPalette p = ColorPalette.eInk6QuantizeColors();
        for (int i=0;i<6;i++) {
            BufferedImage out = new FloydSteinbergDither().apply(solid(p.getR(i),p.getG(i),p.getB(i)),config());
            int expected=(p.getR(i)<<16)|(p.getG(i)<<8)|p.getB(i);
            for (int y=0;y<192;y++) for (int x=0;x<192;x++) assertEquals(expected,out.getRGB(x,y)&0xffffff);
        }
    }

    @Test
    void blueSkyKeepsHueAfterGamutMappingAndDiffusion() {
        DitherConfig c=config();
        double[][] colors = paletteRgb(c.getEffectiveQuantizePalette());
        double[] input=LinearColorSpace.rgb(135,206,235);
        double[] mapped=new PaletteGamut(colors).map(input);
        double[] before=LinearColorSpace.lab(input), after=LinearColorSpace.lab(mapped);
        assertEquals(Math.atan2(before[2],before[1]),Math.atan2(after[2],after[1]),0.002);
        assertEquals(before[0],after[0],0.001);
        BufferedImage out=new FloydSteinbergDither().apply(solid(135,206,235),c);
        double[] mean=new double[3];
        for(int y=0;y<192;y++) for(int x=0;x<192;x++) {
            int rgb=out.getRGB(x,y);
            for(int k=0;k<3;k++) mean[k]+=linear((rgb>>(16-8*k))&255)/(192*192);
        }
        for(int k=0;k<3;k++) assertEquals(mapped[k],mean[k],0.015);
        assertTrue(mean[2]>mean[1] && mean[1]>mean[0],"sky must stay blue, not green");
    }

    @Test
    void gamutMappingIsFiniteAndInsideCalibratedHull() {
        ColorPalette measured=new ColorPalette(Arrays.asList(new int[]{25,25,25},new int[]{225,220,210},
            new int[]{180,70,60},new int[]{60,160,70},new int[]{50,70,170},new int[]{210,190,60}));
        PaletteGamut gamut=new PaletteGamut(paletteRgb(measured));
        Random random=new Random(42);
        for(int i=0;i<1000;i++) {
            double[] input=LinearColorSpace.rgb(random.nextInt(256),random.nextInt(256),random.nextInt(256));
            double[] mapped=gamut.map(input);
            for(double channel:mapped) assertTrue(Double.isFinite(channel));
            assertTrue(gamut.contains(mapped));
            if(gamut.contains(input)) assertArrayEquals(input,mapped,1e-10);
        }
    }

    @Test
    void previewPaletteDoesNotAffectSelectedIndices() {
        DitherConfig a=config(), b=config();
        ColorPalette preview=new ColorPalette(Arrays.asList(new int[]{10,10,10},new int[]{230,230,230},
            new int[]{200,20,20},new int[]{20,200,20},new int[]{20,20,200},new int[]{200,200,20}));
        b.setColorPalettes(a.getEffectiveQuantizePalette(),preview);
        BufferedImage input=solid(180,150,130);
        BufferedImage first=new FloydSteinbergDither().apply(input,a);
        BufferedImage second=new FloydSteinbergDither().apply(input,b);
        for(int y=0;y<192;y++) for(int x=0;x<192;x++) {
            int p=first.getRGB(x,y),q=second.getRGB(x,y);
            assertEquals(a.getEffectivePreviewPalette().findExactIndex((p>>16)&255,(p>>8)&255,p&255),
                preview.findExactIndex((q>>16)&255,(q>>8)&255,q&255));
        }
    }

    @Test
    void invalidSixColorPaletteFailsClearly() {
        DitherConfig c=config();
        c.setColorPalettes(new ColorPalette(Arrays.asList(new int[]{0,0,0},new int[]{255,255,255})),
            ColorPalette.eInk6PreviewColors());
        assertThrows(IllegalArgumentException.class,()->new FloydSteinbergDither().apply(solid(1,2,3),c));
    }

    @Test
    void nearNeutralColorUsesSparseColorCorrection() {
        BufferedImage out=new FloydSteinbergDither().apply(solid(150,140,135),config());
        int colored=0;
        double[] mean=new double[3];
        for(int y=0;y<192;y++) for(int x=0;x<192;x++) {
            int rgb=out.getRGB(x,y)&0xffffff;
            if(rgb!=0 && rgb!=0xffffff) colored++;
            for(int k=0;k<3;k++) mean[k]+=linear((rgb>>(16-8*k))&255)/(192*192);
        }
        assertTrue(colored/(192.0*192)<0.15,"near-neutral regions should not be covered in colored dots");
        assertTrue(mean[0] > mean[1] && mean[1] >= mean[2], "near-neutral keeps gentle warm tone");
    }

    @Test
    void warmFlatAreaAvoidsUnnecessaryComplementaryDots() {
        BufferedImage out = new FloydSteinbergDither().apply(solid(180,150,130), config());
        int colored = 0, cool = 0;
        for (int y=0;y<192;y++) for (int x=0;x<192;x++) {
            int rgb = out.getRGB(x,y) & 0xffffff;
            if (rgb != 0 && rgb != 0xffffff) colored++;
            if (rgb == 0x00ff00 || rgb == 0x0000ff) cool++;
        }
        // 该暖色可以由黑白红黄精确混合，不需要绿蓝互补点相互抵消。
        assertEquals(0, cool, "warm flat area must not be filled with compensating green/blue dots");
        assertTrue(colored / (192.0*192) < 0.50, "use sparse colored ink while preserving average color");
    }

    @Test
    void inkMixturesReconstructMappedColorsForBothPalettes() {
        ColorPalette measured = new ColorPalette(Arrays.asList(new int[]{25,25,25}, new int[]{225,220,210},
            new int[]{180,70,60}, new int[]{60,160,70}, new int[]{50,70,170}, new int[]{210,190,60}));
        for (ColorPalette palette : new ColorPalette[]{ColorPalette.eInk6QuantizeColors(), measured}) {
            double[][] colors = paletteRgb(palette);
            PaletteGamut gamut = new PaletteGamut(colors);
            PaletteMixture mixture = new PaletteMixture(colors);
            Random random = new Random(719);
            for (int sample = 0; sample < 1000; sample++) {
                double[] target = gamut.map(LinearColorSpace.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)));
                double[] weights = mixture.weights(target);
                double total = 0;
                double[] reconstructed = new double[3];
                for (int ink = 0; ink < 6; ink++) {
                    assertTrue(weights[ink] >= 0 && weights[ink] <= 1);
                    total += weights[ink];
                    for (int channel = 0; channel < 3; channel++) reconstructed[channel] += weights[ink] * colors[ink][channel];
                }
                assertEquals(1.0, total, 1e-10);
                assertArrayEquals(target, reconstructed, 1e-6, "less color noise must not change the target mixture");
            }
        }
    }

    @Test
    void onePixelLinesAndInkEdgesRemainSharp() {
        BufferedImage input = new BufferedImage(73, 47, BufferedImage.TYPE_INT_RGB);
        int[] inks = {0, 0xffffff, 0xff0000, 0x00ff00, 0x0000ff, 0xffff00};
        for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
            input.setRGB(x, y, inks[(x + y) % inks.length]);
        }
        for (boolean serpentine : new boolean[]{false, true}) {
            DitherConfig c = config();
            c.setSerpentine(serpentine);
            BufferedImage out = new FloydSteinbergDither().apply(input, c);
            for (int y = 0; y < input.getHeight(); y++) for (int x = 0; x < input.getWidth(); x++) {
                assertEquals(input.getRGB(x, y), out.getRGB(x, y));
            }
        }
    }

    @Test
    void warmGradientKeepsLocalToneAndIsDeterministic() {
        BufferedImage input = new BufferedImage(192, 192, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
            int r = 80 + x * 150 / 191, g = 60 + x * 140 / 191, b = 50 + x * 130 / 191;
            input.setRGB(x, y, (r << 16) | (g << 8) | b);
        }
        BufferedImage out = new FloydSteinbergDither().apply(input, config());
        BufferedImage repeated = new FloydSteinbergDither().apply(input, config());
        double prevLuma = -1.0;
        for (int block = 0; block < 6; block++) {
            double[] actual = new double[3];
            for (int y = 0; y < 192; y++) for (int x = block * 32; x < (block + 1) * 32; x++) {
                assertEquals(out.getRGB(x, y), repeated.getRGB(x, y), "must be deterministic");
                for (int channel = 0; channel < 3; channel++) {
                    int shift = 16 - channel * 8;
                    actual[channel] += linear((out.getRGB(x, y) >> shift) & 255) / (32 * 192);
                }
            }
            assertTrue(actual[0] > actual[1] && actual[1] >= actual[2], "gradient must preserve warm hue order");
            double blockLuma = 0.299 * actual[0] + 0.587 * actual[1] + 0.114 * actual[2];
            assertTrue(blockLuma >= prevLuma, "gradient luminance must be monotonic from left to right");
            prevLuma = blockLuma;
        }
    }

    @Test
    void greenHasSuppressedBlackAndHighGreenFidelity() {
        DitherConfig c = config();
        // 鲜亮纯正绿色：杜绝黑点污染，纯绿墨水占比应接近 100%，黑色占比为 0
        int[][] vividGreens = {
            {0, 255, 0},
            {0, 200, 0},
            {0, 180, 0},
            {50, 180, 50}
        };
        for (int[] g : vividGreens) {
            BufferedImage out = new FloydSteinbergDither().apply(solid(g[0], g[1], g[2]), c);
            int black = 0, green = 0;
            for (int y = 0; y < 192; y++) {
                for (int x = 0; x < 192; x++) {
                    int rgb = out.getRGB(x, y) & 0xffffff;
                    if (rgb == 0) black++;
                    if (rgb == 0x00ff00) green++;
                }
            }
            double blackPct = black / (192.0 * 192.0);
            double greenPct = green / (192.0 * 192.0);
            assertEquals(0.0, blackPct, 0.01, "vivid green must not contain black dots: " + Arrays.toString(g));
            assertTrue(greenPct > 0.95, "vivid green must be dominated by green ink: " + Arrays.toString(g));
        }

        // 自然/深色绿色（森林绿、暗绿）：黑色成分大幅抑制，绿色墨水占绝对主导（>75%），黑色控制在 20% 以内（原算法高达 75%~80%）
        int[][] darkGreens = {
            {34, 139, 34},
            {0, 150, 0},
            {60, 130, 40},
            {0, 128, 0}
        };
        for (int[] g : darkGreens) {
            BufferedImage out = new FloydSteinbergDither().apply(solid(g[0], g[1], g[2]), c);
            int black = 0, green = 0;
            for (int y = 0; y < 192; y++) {
                for (int x = 0; x < 192; x++) {
                    int rgb = out.getRGB(x, y) & 0xffffff;
                    if (rgb == 0) black++;
                    if (rgb == 0x00ff00) green++;
                }
            }
            double blackPct = black / (192.0 * 192.0);
            double greenPct = green / (192.0 * 192.0);
            assertTrue(blackPct <= 0.20, "dark green black dots must be suppressed <= 20%: " + Arrays.toString(g) + ", actual=" + blackPct);
            assertTrue(greenPct >= 0.75, "dark green must retain high green ink coverage >= 75%: " + Arrays.toString(g) + ", actual=" + greenPct);
        }
    }

    @Test
    void allColorsHaveSuppressedBlackAndHighFidelity() {
        DitherConfig c = config();
        // 纯红：黑色彻底归零，纯红墨水占 95% 以上
        BufferedImage redOut = new FloydSteinbergDither().apply(solid(200, 40, 40), c);
        int redBlack = 0, redInk = 0;
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
            int rgb = redOut.getRGB(x, y) & 0xffffff;
            if (rgb == 0) redBlack++;
            if (rgb == 0xff0000) redInk++;
        }
        assertEquals(0.0, redBlack / (192.0 * 192), 0.01, "pure red must not contain black dots");
        assertTrue(redInk / (192.0 * 192) > 0.95, "pure red ink coverage > 95%");

        // 纯蓝：黑色彻底归零，纯蓝墨水占 90% 以上
        BufferedImage blueOut = new FloydSteinbergDither().apply(solid(40, 60, 200), c);
        int blueBlack = 0, blueInk = 0;
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
            int rgb = blueOut.getRGB(x, y) & 0xffffff;
            if (rgb == 0) blueBlack++;
            if (rgb == 0x0000ff) blueInk++;
        }
        assertEquals(0.0, blueBlack / (192.0 * 192), 0.01, "pure blue must not contain black dots");
        assertTrue(blueInk / (192.0 * 192) > 0.90, "pure blue ink coverage > 90%");

        // 亮黄：黑色彻底归零，黄墨水占绝对主导
        BufferedImage yellowOut = new FloydSteinbergDither().apply(solid(220, 200, 40), c);
        int yellowBlack = 0, yellowInk = 0;
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
            int rgb = yellowOut.getRGB(x, y) & 0xffffff;
            if (rgb == 0) yellowBlack++;
            if (rgb == 0xffff00) yellowInk++;
        }
        assertEquals(0.0, yellowBlack / (192.0 * 192), 0.01, "bright yellow must not contain black dots");
        assertTrue(yellowInk / (192.0 * 192) > 0.80, "bright yellow ink coverage > 80%");

        // 肤色：黑点大幅抑制在 5% 以内（原算法 35.6%），以白、红、黄墨水为主
        BufferedImage skinOut = new FloydSteinbergDither().apply(solid(210, 170, 150), c);
        int skinBlack = 0, skinWhite = 0;
        for (int y = 0; y < 192; y++) for (int x = 0; x < 192; x++) {
            int rgb = skinOut.getRGB(x, y) & 0xffffff;
            if (rgb == 0) skinBlack++;
            if (rgb == 0xffffff) skinWhite++;
        }
        assertTrue(skinBlack / (192.0 * 192) <= 0.05, "skin black dots <= 5%");
        assertTrue(skinWhite / (192.0 * 192) >= 0.40, "skin white ink coverage >= 40%");
    }

    private double[][] paletteRgb(ColorPalette p) {
        double[][] result=new double[p.size()][];
        for(int i=0;i<p.size();i++) result[i]=LinearColorSpace.rgb(p.getR(i),p.getG(i),p.getB(i));
        return result;
    }
}
