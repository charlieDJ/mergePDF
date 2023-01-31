package com.pdf;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.pdf.dto.FilePath;
import com.pdf.util.FileUtils;
import com.pdf.util.LogUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

    public static final String PDF_SUFFIX = "pdf";
    public static final List<String> SUFFIX_LIST = Stream.of("png", "jpg", "pdf").collect(Collectors.toList());
    public static final List<String> IMAGE_SUFFIX_LIST = Stream.of("png", "jpg").collect(Collectors.toList());

    public static void main(String[] args) {
        LogUtils.print("开始合并PDF");
        String currentDir = FileUtils.getCurrentDir();
        if (currentDir.length() == 0) {
            LogUtils.print("未找到当前文件夹");
            return;
        }
        Path currentPath = Paths.get(currentDir);
        try {
            Path dirAbsPath = currentPath.toAbsolutePath();
            FilePath filePath = getPaths(dirAbsPath);
            List<Path> paths = filePath.getPdfPaths();
            if (paths.isEmpty()) {
                LogUtils.print("未找到PDF文件");
                return;
            }
            merge(currentDir, paths);
            // 删除临时文件
            List<Path> imagePaths = filePath.getImagePaths();
            for (Path imageToPdfPath : imagePaths) {
                Files.deleteIfExists(imageToPdfPath);
            }
        } catch (IOException | DocumentException e) {
            e.printStackTrace();
            LogUtils.error("合并失败，错误原因：" + e.getMessage());
        }
        LogUtils.print("合并结束");
    }

    /**
     * 合并pdf
     *
     * @param outDir 输入目录
     * @param paths  pdf路径
     * @throws IOException
     * @throws DocumentException
     */
    private static void merge(String outDir, List<Path> paths) throws IOException, DocumentException {
        Document document = new Document(PageSize.A4);
        String outPath = outDir + File.separator + "merge.pdf";
        try (FileOutputStream fos = new FileOutputStream(new File(outPath))) {
            PdfCopy copy = new PdfCopy(document, fos);
            // 打开文档准备写入内容
            document.open();
            for (Path path : paths) {
                PdfReader reader = new PdfReader(new FileInputStream(path.toFile()));
                // 获取页数
                int numberOfPages = reader.getNumberOfPages();
                // pdf的所有页, 从第1页开始遍历, 这里要注意不是0
                for (int i = 1; i <= numberOfPages; i++) {
                    // 把第 i 页读取出来
                    PdfImportedPage page = copy.getImportedPage(reader, i);
                    document.newPage();
                    // 把读取出来的页追加进输出文件里
                    copy.addPage(page);
                }
            }
            document.close();
        }
    }

    /**
     * 获取图片和pdf的路径
     *
     * @param dirAbsPath 输入目录
     * @return 图片和pdf的路径
     */
    private static FilePath getPaths(Path dirAbsPath) {
        // 获取图像和pdf的路径
        List<Path> paths;
        FilePath path = new FilePath();
        try {
            paths = Files.walk(dirAbsPath, 1)
                    .filter(e -> SUFFIX_LIST.contains(getSuffix(e)))
                    .filter(e -> !e.getFileName().toString().contains("merge"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.error(e.getMessage());
            return path;
        }
        // 获取图像的路径和文件名
        Map<String, Image> imageMap = getImageMap(paths);
        // 图片转PDF
        List<Path> imageToPdfPaths = imageToPdf(imageMap, dirAbsPath);
        // 获取pdf的路径
        paths = paths.stream()
                .filter(e -> PDF_SUFFIX.equals(getSuffix(e)))
                .collect(Collectors.toList());
        paths.addAll(imageToPdfPaths);
        // 按照文件名称排序
        paths = paths.stream()
                .sorted(Comparator.comparing(Path::getFileName))
                .collect(Collectors.toList());
        path.setImagePaths(imageToPdfPaths);
        path.setPdfPaths(paths);
        return path;
    }

    /**
     * 获取文件后缀
     *
     * @param e 文件路径
     * @return 后缀
     */
    private static String getSuffix(Path e) {
        String filename = e.getFileName().toString();
        int lastDotIndex = filename.lastIndexOf(".");
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 获取图片名称和图片
     * key：图片名称，value：图片
     *
     * @param paths 图片的路径
     * @return 图片名称和图片
     */
    private static Map<String, Image> getImageMap(List<Path> paths) {
        Map<String, Image> imageMap = new HashMap<>();
        paths.stream()
                .filter(e -> IMAGE_SUFFIX_LIST.contains(getSuffix(e)))
                .forEach(e -> {
                    try {
                        String filename = e.getFileName().toString();
                        int lastIndexOf = filename.lastIndexOf(".");
                        imageMap.put(filename.substring(0, lastIndexOf), Image.getInstance(Files.readAllBytes(e)));
                    } catch (BadElementException | IOException ex) {
                        ex.printStackTrace();
                    }
                });
        return imageMap;
    }

    /**
     * 图片转PDF
     *
     * @param imageMap   图片
     * @param dirAbsPath 保存图片的路径
     * @return PDF文件地址
     */
    private static List<Path> imageToPdf(Map<String, Image> imageMap, Path dirAbsPath) {
        List<Path> paths = new ArrayList<>();
        for (String filename : imageMap.keySet()) {
            Document doc = new Document();
            try {
                String path = dirAbsPath + File.separator + filename + ".pdf";
                paths.add(Paths.get(path));
                PdfWriter.getInstance(doc, new FileOutputStream(path));
                doc.open();
                Image image = imageMap.get(filename);
                float width = image.getWidth();
                float height = image.getHeight();
                if (width > height) {
                    doc.setPageSize(PageSize.A4.rotate());
                    float percent = 800 / width;
                    image.scalePercent(percent * 100);
                } else {
                    doc.setPageSize(PageSize.A4);
                    float percent = 500 / width;
                    image.scalePercent(percent * 100);
                }
                doc.newPage();
                doc.add(image);
            } catch (DocumentException | IOException e) {
                e.printStackTrace();
            } finally {
                doc.close();
            }
        }
        return paths;
    }

}
