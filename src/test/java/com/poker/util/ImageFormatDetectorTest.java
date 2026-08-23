package com.poker.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ImageFormatDetectorTest {

    @Test
    void detectsJpegByMagicBytes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        assertThat(ImageFormatDetector.detect(jpeg)).isEqualTo(ImageFormatDetector.Format.JPEG);
    }

    @Test
    void detectsPngByMagicBytes() {
        byte[] png = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

        assertThat(ImageFormatDetector.detect(png)).isEqualTo(ImageFormatDetector.Format.PNG);
    }

    @Test
    void rejectsUnknownOrShortPayloads() {
        assertThat(ImageFormatDetector.detect(null)).isNull();
        assertThat(ImageFormatDetector.detect(new byte[]{(byte) 0xFF, (byte) 0xD8})).isNull();
        assertThat(ImageFormatDetector.detect("hello".getBytes())).isNull();
    }
}
