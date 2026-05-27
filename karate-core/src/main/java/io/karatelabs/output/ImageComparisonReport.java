/*
 * The MIT License
 *
 * Copyright 2025 Karate Labs Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.karatelabs.output;

import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

import static org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE;

public class ImageComparisonReport {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(ImageComparisonReport.class);

    // use landscape to make more space for side-by-side comparison
    static final float PAGE_HEIGHT = PDRectangle.A4.getWidth();
    static final float PAGE_WIDTH = PDRectangle.A4.getHeight();
    static final float PAGE_MARGIN = 10;
    static final float LINE_MARGIN = 10;
    static final float HEADER_HEIGHT = 75;
    static final float FOOTER_HEIGHT = 75;
    static final float HORIZONTAL_PADDING = 100;
    static final float COLUMN_PADDING = HORIZONTAL_PADDING / 4;
    static final float MAX_IMAGE_HEIGHT = PAGE_HEIGHT - HEADER_HEIGHT - FOOTER_HEIGHT;
    static final float MAX_IMAGE_WIDTH = (PAGE_WIDTH - HORIZONTAL_PADDING) / 3;
    static final PDFont ITALIC_FONT = new PDType1Font(Standard14Fonts.FontName.TIMES_ITALIC);
    static final PDFont TEXT_FONT = new PDType1Font(Standard14Fonts.FontName.COURIER);
    static final float TEXT_FONT_SIZE = 12;
    static final float MIN_FONT_SIZE = 8;
    static final PDFont HEADER_FONT = new PDType1Font(Standard14Fonts.FontName.COURIER_BOLD);
    static final float HEADER_FONT_SIZE = 18;
    static final String TITLE_DELIMITER = " / ";
    static final Color ERROR_TEXT_COLOR = new Color(175,0,0);

    private final Map<String,Integer> pageNumberById;
    private final String path;
    private PDDocument document;
    private int pageNumber;
    private float footerOffset;

    public ImageComparisonReport(String path) {
        this.pageNumberById = new HashMap<>();
        this.path = path;
    }

    public boolean hasResults() {
        return document != null;
    }

    public synchronized void writeResult(ImageComparisonResult result) {
        if (result == null) {
            return;
        }

        if (document == null) {
            document = new PDDocument(IOUtils.createTempFileOnlyStreamCache());
        }

        String resultId = String.join(".", result.uniqueIdParts);
        boolean isNewPage = !pageNumberById.containsKey(resultId);
        footerOffset = FOOTER_HEIGHT + MAX_IMAGE_HEIGHT;

        PDPage page;

        try {
            if (isNewPage) {
                page = new PDPage(new PDRectangle(PAGE_WIDTH, PAGE_HEIGHT));
            } else {
                page = document.getPage(pageNumberById.get(resultId));
            }

            PDPageContentStream contentStream = new PDPageContentStream(document, page, OVERWRITE, false, true);
            writeTitle(contentStream, result.uniqueIdParts);
            writeColumnHeaders(contentStream);
            writeImage(contentStream, result.baselineImage, 0);
            writeImage(contentStream, result.diffImage, 1);
            writeImage(contentStream, result.LatestImage, 2);
            writeFooter(contentStream, result);
            contentStream.close();
        } catch (IOException e) {
            LOGGER.error("error writing image comparison result: {}", e.getMessage());
            throw new RuntimeException(e);
        }

        if (isNewPage) {
            document.addPage(page);
            pageNumberById.put(resultId, pageNumber);
            pageNumber++;
        }
    }

    private void writeTitle(PDPageContentStream contentStream, String[] titleParts) throws IOException {
        String[] parts = Arrays.stream(titleParts)
                .filter(Objects::nonNull)
                .map(this::sanitizeText)
                .toArray(String[]::new);

        String title = String.join(TITLE_DELIMITER, parts);
        float titleWidth = calcTextWidth(title, TEXT_FONT, TEXT_FONT_SIZE);
        float maxWidth = PAGE_WIDTH - (PAGE_MARGIN * 2);
        float fontSize = TEXT_FONT_SIZE;

        // shrink font to fit title
        while (titleWidth > maxWidth && fontSize >= MIN_FONT_SIZE) {
            fontSize -= 0.5f;
            titleWidth = calcTextWidth(title, TEXT_FONT, fontSize);
        }

        // truncate title to fit
        if (titleWidth > maxWidth) {
            for (int i = parts.length - 1; i > 0; i--) {
                title = String.join(TITLE_DELIMITER, Arrays.copyOfRange(parts, 0, i));
                titleWidth = calcTextWidth(title, TEXT_FONT, fontSize);
                if (titleWidth <= maxWidth) {
                    LOGGER.warn("image comparison report title truncated: {}", String.join(TITLE_DELIMITER, parts));
                    break;
                }
            }
        }

        // no part of title would fit -- just log the error
        if (titleWidth > maxWidth) {
            LOGGER.warn("image comparison report title too long: {}", String.join(TITLE_DELIMITER, parts));
            return;
        }

        contentStream.beginText();
        contentStream.newLineAtOffset(PAGE_MARGIN, PAGE_HEIGHT - PAGE_MARGIN - calcFontHeight(TEXT_FONT, fontSize));
        contentStream.setFont(TEXT_FONT, fontSize);
        contentStream.showText(title);
        contentStream.endText();
    }

    private String sanitizeText(String text) {
        return text.codePoints()
                .map(c -> isValidChar(c) ? c : '?')
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private boolean isValidChar(int c) {
        try {
            TEXT_FONT.encode(Character.toString(c));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void writeColumnHeaders(PDPageContentStream contentStream) throws IOException {
        contentStream.setFont(HEADER_FONT, HEADER_FONT_SIZE);
        float headerY = PAGE_HEIGHT - HEADER_HEIGHT + LINE_MARGIN;
        int cnt = 0;

        for (String title : List.of("Baseline", "Diff" , "Latest")) {
            float titleWidth = calcTextWidth(title, HEADER_FONT, HEADER_FONT_SIZE);
            float indent = (COLUMN_PADDING * (cnt + 1)) + (MAX_IMAGE_WIDTH * cnt);
            float titlePadding = indent + (MAX_IMAGE_WIDTH - titleWidth) / 2;
            contentStream.beginText();
            contentStream.newLineAtOffset(titlePadding, headerY);
            contentStream.showText(title);
            contentStream.endText();
            cnt++;
        }
    }

    private void writeImage(PDPageContentStream contentStream, BufferedImage image, int position) throws IOException {
        float imageHeight = image.getHeight();
        float imageWidth = image.getWidth();

        if (imageWidth > MAX_IMAGE_WIDTH || imageHeight > MAX_IMAGE_HEIGHT) {
            float widthScale = MAX_IMAGE_WIDTH / imageWidth;
            float heightScale = MAX_IMAGE_HEIGHT / imageHeight;

            if (widthScale < heightScale) {
                imageHeight = (MAX_IMAGE_WIDTH * imageHeight) / imageWidth;
                imageWidth = MAX_IMAGE_WIDTH;
            } else if (heightScale < widthScale) {
                imageWidth = (MAX_IMAGE_HEIGHT * imageWidth) / imageHeight;
                imageHeight = MAX_IMAGE_HEIGHT;
            } else {
                imageHeight = MAX_IMAGE_HEIGHT;
                imageWidth = MAX_IMAGE_WIDTH;
            }
        }

        float x = ((position + 1) * COLUMN_PADDING) + (position * MAX_IMAGE_WIDTH);
        if (imageWidth < MAX_IMAGE_WIDTH) {
            x += (MAX_IMAGE_WIDTH - imageWidth) / 2;
        }

        float y = FOOTER_HEIGHT;
        if (imageHeight < MAX_IMAGE_HEIGHT) {
            y += (MAX_IMAGE_HEIGHT - imageHeight);
        }

        if (y < footerOffset) {
            footerOffset = y;
        }

        PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
        contentStream.drawImage(pdImage, x, y, imageWidth, imageHeight);
    }

    private void writeFooter(PDPageContentStream contentStream, ImageComparisonResult result) throws IOException {
        contentStream.setFont(TEXT_FONT, TEXT_FONT_SIZE);
        contentStream.beginText();
        contentStream.newLineAtOffset(0, footerOffset - LINE_MARGIN);

        String text = "The second image is " + result.resembleMismatchPercentage + "% different compared to the first.";

        if (result.ssimMismatchPercentage >= 0) {
            text += " (SSIM reported " + result.ssimMismatchPercentage + "% difference)";
        }

        float textWidth = calcTextWidth(text, TEXT_FONT, TEXT_FONT_SIZE);
        contentStream.newLineAtOffset((PAGE_WIDTH - textWidth) / 2, -calcFontHeight(TEXT_FONT, TEXT_FONT_SIZE));
        contentStream.showText(text);

        if (result.errorMessage != null) {
            contentStream.newLineAtOffset(-(PAGE_WIDTH - textWidth) / 2, 0);
            textWidth = calcTextWidth(result.errorMessage, ITALIC_FONT, TEXT_FONT_SIZE);
            float textHeight = calcFontHeight(ITALIC_FONT, TEXT_FONT_SIZE);
            contentStream.newLineAtOffset((PAGE_WIDTH - textWidth) / 2, -textHeight - LINE_MARGIN);
            contentStream.setNonStrokingColor(ERROR_TEXT_COLOR);
            contentStream.setFont(ITALIC_FONT, TEXT_FONT_SIZE);
            contentStream.showText(result.errorMessage);
        }

        contentStream.endText();
    }

    private static float calcTextWidth(String text, PDFont font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }

    private static float calcFontHeight(PDFont font, float fontSize) {
        return font.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;
    }

    public void close() {
        if (document != null) {
            try {
                Files.createDirectories(Path.of(path).getParent());
                document.save(path);
                document.close();
            } catch (IOException e) {
                LOGGER.error("failed to close image comparison report: {}", e.getMessage());
                throw new RuntimeException(e);
            }
        }
    }
}
