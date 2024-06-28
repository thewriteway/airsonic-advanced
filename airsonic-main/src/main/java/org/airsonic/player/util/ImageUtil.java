/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

public final class ImageUtil {

    private ImageUtil() {
    }

    /**
     * Scale an image to the given width and height.
     *
     * @param image The image to scale.
     * @param width The desired width.
     * @param height The desired height.
     * @return The scaled image.
     */
    public static BufferedImage scale(BufferedImage image, int width, int height) {
        int w = image.getWidth();
        int h = image.getHeight();
        BufferedImage thumb = image;

        // For optimal results, use step by step bilinear resampling - halfing the size at each step.
        do {
            w /= 2;
            h /= 2;
            if (w < width) {
                w = width;
            }
            if (h < height) {
                h = height;
            }

            BufferedImage temp = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = temp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(thumb, 0, 0, temp.getWidth(), temp.getHeight(), null);
            g2.dispose();

            thumb = temp;
        } while (w != width);

        return thumb;
    }

    public static BufferedImage scaleToSquare(BufferedImage image, int size) {
        int w = image.getWidth();
        int h = image.getHeight();
        int scale = Math.max(w, h);

        BufferedImage squareImage = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_RGB);
        squareImage.getGraphics().drawImage(image, (scale - w) / 2, (scale - h) / 2, null);

        do {
            scale /= 2;
            if (scale < size) {
                scale = size;
            }
            BufferedImage temp = new BufferedImage(scale, scale, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = temp.createGraphics();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(squareImage, 0, 0, temp.getWidth(), temp.getHeight(), null);
            g2.dispose();

            squareImage = temp;
        } while (scale != size);

        return squareImage;
    }

}
