package com.poker.util;

public final class ImageFormatDetector {

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    public enum Format {
        JPEG(".jpg", "image/jpeg"),
        PNG(".png", "image/png");

        private final String extension;
        private final String contentType;

        Format(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String extension() {
            return extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    private ImageFormatDetector() {
    }

    public static Format detect(byte[] data) {
        if (startsWith(data, JPEG_MAGIC)) {
            return Format.JPEG;
        }
        if (startsWith(data, PNG_MAGIC)) {
            return Format.PNG;
        }
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data == null || data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
