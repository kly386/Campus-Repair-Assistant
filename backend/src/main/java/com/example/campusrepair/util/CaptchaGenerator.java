package com.example.campusrepair.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.imageio.ImageIO;

/**
 * 图形验证码生成器
 *
 * @Description 本地生成 4 位随机码 PNG（Base64 data URI 返回前端展示），干扰线+扭曲降低 OCR 可读性。
 *              不引入外部验证码组件，保持零额外依赖。
 * @Author 开璐瑶
 * @Date 2026-11-10
 * @Version 1.0
 */
public final class CaptchaGenerator {

    private CaptchaGenerator() {
    }

    /** 去除易混淆字符(O/0/I/1)的可用字符集 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * 生成 4 位验证码文本
     */
    public static String randomCode() {
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 4; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 渲染验证码图片并返回 data URI（image/png;base64,...）
     *
     * @param code 验证码文本
     * @return data URI 字符串
     */
    public static String toDataUri(String code) {
        int w = 120, h = 40;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // 背景
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, w, h);

        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(RANDOM.nextInt(200), RANDOM.nextInt(200), RANDOM.nextInt(200)));
            g.setStroke(new BasicStroke(1.2f));
            g.drawLine(RANDOM.nextInt(w), RANDOM.nextInt(h), RANDOM.nextInt(w), RANDOM.nextInt(h));
        }

        // 逐字符绘制（随机色 + 轻微上下错位）
        g.setFont(new Font("Arial", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(RANDOM.nextInt(120), RANDOM.nextInt(120), RANDOM.nextInt(160)));
            g.drawString(String.valueOf(code.charAt(i)), 12 + i * 24, 28 + RANDOM.nextInt(8) - 4);
        }

        // 噪点
        for (int i = 0; i < 30; i++) {
            img.setRGB(RANDOM.nextInt(w), RANDOM.nextInt(h), new Color(RANDOM.nextInt(255)).getRGB());
        }
        g.dispose();

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", bos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码图片失败", e);
        }
    }
}
