package org.airsonic.player.domain.dto;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public class AutoCover {
    private final static int[] COLORS = { 0x33B5E4, 0xAA66CC, 0x99CC00, 0xFFBB33, 0xFF4444 };
    private final Graphics2D graphics;
    private final String artist;
    private final String album;
    private final int width;
    private final int height;
    private final Color color;

    public AutoCover(Graphics2D graphics, String key, String artist, String album, int width, int height) {
        this.graphics = graphics;
        this.artist = artist;
        this.album = album;
        this.width = width;
        this.height = height;

        int hash = key.hashCode();
        int rgb = COLORS[Math.abs(hash) % COLORS.length];
        this.color = new Color(rgb);
    }

    public void paintCover() {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        graphics.setPaint(color);
        graphics.fillRect(0, 0, width, height);

        int y = height * 2 / 3;
        graphics.setPaint(new GradientPaint(0, y, new Color(82, 82, 82), 0, height, Color.BLACK));
        graphics.fillRect(0, y, width, height / 3);

        graphics.setPaint(Color.WHITE);
        float fontSize = 3.0f + height * 0.07f;
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) fontSize);
        graphics.setFont(font);

        if (album != null) {
            graphics.drawString(album, width * 0.05f, height * 0.6f);
        }
        if (artist != null) {
            graphics.drawString(artist, width * 0.05f, height * 0.8f);
        }

        int borderWidth = height / 50;
        graphics.fillRect(0, 0, borderWidth, height);
        graphics.fillRect(width - borderWidth, 0, height - borderWidth, height);
        graphics.fillRect(0, 0, width, borderWidth);
        graphics.fillRect(0, height - borderWidth, width, height);
    }

}
