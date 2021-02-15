/*
 * The MIT License
 *
 * Copyright 2020 Intuit Inc.
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
package com.intuit.karate.robot;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.function.Supplier;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.tesseract.ResultIterator;
import org.bytedeco.tesseract.TessBaseAPI;
import org.bytedeco.tesseract.global.tesseract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author pthomas3
 */
public class Tesseract {

    private static final Logger logger = LoggerFactory.getLogger(Tesseract.class);

    private final TessBaseAPI tess;
    private final Supplier<IntPointer> INT = () -> new IntPointer(new int[1]);

    private String allText;
    private List<Word> words;

    public String getAllText() {
        return allText;
    }

    public List<Word> getWords() {
        return words;
    }

    public static class Word {

        final String text;
        final int x;
        final int y;
        final int width;
        final int height;
        final float confidence;

        final Word prev;
        Word next;

        public Word(Word prev, String text, int x, int y, int width, int height, float confidence) {
            if (prev != null) {
                prev.next = this;
            }
            this.prev = prev;
            this.text = text;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }

        @Override
        public String toString() {
            return text + " " + x + ":" + y + "(" + width + ":" + height + ") " + confidence;
        }

    }

    public Tesseract(File dataDir, String language) {
        tess = new TessBaseAPI();
        String dataPath = dataDir.getPath();
        if (tess.Init(dataPath, language) != 0) {
            throw new RuntimeException("tesseract init failed: " + dataDir.getAbsolutePath() + ", " + language);
        }
    }

    public static final Tesseract init(RobotBase robot, String lang, Region region, boolean negative) {
        File file = new File(robot.tessData);
        Tesseract tess = new Tesseract(file, lang);
        tess.process(region, negative);
        return tess;
    }

    public static Element find(RobotBase robot, String lang, Region sr, String text, boolean negative) {
        Tesseract tess = init(robot, lang, sr, negative);
        List<int[]> list = tess.find(false, text);
        if (list.isEmpty()) {
            return null;
        }
        int[] b = list.get(0);
        Region region = new Region(robot, sr.x + b[0], sr.y + b[1], b[2], b[3]);
        return new ImageElement(region);
    }

    public static List<Element> findAll(RobotBase robot, String lang, Region sr, String text, boolean negative) {
        Tesseract tess = init(robot, lang, sr, negative);
        List<int[]> list = tess.find(true, text);
        List<Element> found = new ArrayList(list.size());
        for (int[] b : list) {
            Region region = new Region(robot, sr.x + b[0], sr.y + b[1], b[2], b[3]);
            found.add(new ImageElement(region));
        }
        return found;
    }

    public void process(Region region, boolean negative) {
        BufferedImage bi = region.captureGreyScale();
        if (region.robot.highlight) {
            region.highlight(region.robot.highlightDuration);
        }
        Mat mat = OpenCvUtils.toMat(bi);
        process(mat, negative);
    }

    public void highlightWords(RobotBase robot, Region parent, int millis) {
        List<Element> elements = new ArrayList();
        for (Tesseract.Word word : words) {
            Region region = new Region(robot, parent.x + word.x, parent.y + word.y, word.width, word.height);
            Element e = new ImageElement(region, word.text);
            elements.add(e);
        }
        RobotUtils.highlightAll(parent, elements, millis, true);
    }

    public void process(Mat mat, boolean negative) {
        if (negative) {
            mat = OpenCvUtils.negative(mat);
        }
        int srcWidth = mat.size().width();
        int srcHeight = mat.size().height();
        int channels = mat.channels();
        int bytesPerLine = srcWidth * channels * (int) mat.elemSize1();
        tess.SetImage(mat.data().asBuffer(), srcWidth, srcHeight, channels, bytesPerLine);
        //======================================================================
        BytePointer textPtr = tess.GetUTF8Text();
        allText = textPtr.getString();
        textPtr.deallocate();
        //======================================================================
        ResultIterator ri = tess.GetIterator();
        int level = tesseract.RIL_WORD;
        words = new ArrayList();
        Word prev = null;
        do {
            float confidence = ri.Confidence(level);
            if (confidence < 50) {
                continue;
            }
            textPtr = ri.GetUTF8Text(level);
            String text = textPtr.getString().trim();
            textPtr.deallocate();
            IntPointer x1 = INT.get();
            IntPointer y1 = INT.get();
            IntPointer x2 = INT.get();
            IntPointer y2 = INT.get();
            boolean found = ri.BoundingBox(level, x1, y1, x2, y2);
            int x = x1.get();
            int y = y1.get();
            int width = x2.get() - x;
            int height = y2.get() - y;
            if (!found) {
                logger.warn("no such rectangle: {}:{}:{}:{}", x, y, width, height);
                continue;
            }
            Word word = new Word(prev, text, x, y, width, height, confidence);
            words.add(word);
            prev = word;
        } while (ri.Next(level));
    }

    public List<int[]> find(boolean findAll, String text) {
        StringTokenizer st = new StringTokenizer(text);
        String[] args = new String[st.countTokens()];
        for (int i = 0; st.hasMoreTokens(); i++) {
            args[i] = st.nextToken();
        }
        List<int[]> list = new ArrayList();
        for (Word w : words) {
            boolean found = false;
            int i = 0;
            Word current = w;
            Word prev = null;
            do {
                String s = args[i];
                found = s.contains(current.text);
                prev = current;
                current = current.next;
            } while (found && ++i < args.length && current != null);
            if (found && i == args.length) {
                Word first = w;
                Word last = prev;
                int x = first.x;
                int y = first.y;
                int width = last.x + last.width - first.x;
                int height = Math.max(first.height, last.height);
                int[] bounds = new int[]{x, y, width, height};
                if (!findAll) {
                    return Collections.singletonList(bounds);
                }
                list.add(bounds);
            }
        }
        return list;
    }

}
