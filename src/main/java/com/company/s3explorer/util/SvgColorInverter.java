package com.company.s3explorer.util;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SvgColorInverter {

    public static void invertSvgColors(Path inputPath, Path outputPath) throws IOException {
        String content = Files.readString(inputPath);

        // Hex renk kodlarını yakalamak için Regex (örn: #FF00AA veya #F0A)
        Pattern pattern = Pattern.compile("#([0-9a-fA-F]{6}|[0-9a-fA-F]{3})");
        Matcher matcher = pattern.matcher(content);

        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hexColor = matcher.group(1);
            String invertedHex = invertHex(hexColor);
            matcher.appendReplacement(sb, "#" + invertedHex);
        }
        matcher.appendTail(sb);

        Files.writeString(outputPath, sb.toString());
    }

    private static String invertHex(String hex) {
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
        }
        int r = 255 - Integer.parseInt(hex.substring(0, 2), 16);
        int g = 255 - Integer.parseInt(hex.substring(2, 4), 16);
        int b = 255 - Integer.parseInt(hex.substring(4, 6), 16);

        return String.format("%02X%02X%02X", r, g, b);
    }

    public static void main(String[] args) throws IOException {
        String folderPath = "E:\\SIL"; // Kendi klasör yolunuzu yazın

        try (Stream<Path> paths = Files.list(Paths.get(folderPath))) {
            paths.filter(Files::isRegularFile) // Sadece dosyaları filtrele (klasörleri hariç tutmak için)
                    .forEach(p -> {
                        String[] fileName = printFileNameAndExtension(p.getFileName());
                        try {
                            invertSvgColors(p, new File(p.getParent().toFile(), fileName[0] + "-reverse" + (fileName[1].equals("") ? "" : "." + fileName[1])).toPath());
                        } catch (IOException e) {
                            System.out.println(e.getMessage());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            System.err.println("Klasör okunurken hata oluştu: " + e.getMessage());
        }
    }
    
    public static String[] printFileNameAndExtension(Path filePath) {
        String fileName = filePath.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf('.');

        String nameWithoutExt;
        String extension;

        if (lastDotIndex > 0) {
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex + 1);
        } else {
            nameWithoutExt = fileName;
            extension = ""; // Uzantısı olmayan dosyalar için
        }

        return new String[] {nameWithoutExt, extension};
    }
}