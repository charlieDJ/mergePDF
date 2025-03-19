package com.pdf;

import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfImportedPage;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfWriter;
import com.pdf.dto.FilePath;
import com.pdf.util.FileUtils;
import com.pdf.util.LogUtils;
import com.pdf.util.WordUtils;
import org.docx4j.openpackaging.exceptions.Docx4JException;

import java.io.File;
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
    public static final String DOCX_SUFFIX = "docx";
    public static final List<String> SUFFIX_LIST = Stream.of("png", "jpg", "pdf", "docx").collect(Collectors.toList());
    public static final List<String> IMAGE_SUFFIX_LIST = Stream.of("png", "jpg").collect(Collectors.toList());

    /**
     * 文件路径集合
     * 合并完成后需要删除的文件
     */
    private static final List<Path> toDeleteFilePaths = new ArrayList<>();

    public static void main(String[] args) {
        LogUtils.print("开始合并PDF");
        String currentDir = FileUtils.getCurrentDir();
        if (currentDir.isEmpty()) {
            LogUtils.print("未找到当前文件夹");
            return;
        }
        LogUtils.print("当前路径：" + currentDir);
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
            List<Path> tempPaths = filePath.getImagePaths();
            for (Path tempPath : tempPaths) {
                Files.deleteIfExists(tempPath);
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
            document.open();
            for (Path path : paths) {
                PdfReader reader = new PdfReader(Files.newInputStream(path));
                int numberOfPages = reader.getNumberOfPages();
                for (int i = 1; i <= numberOfPages; i++) {
                    PdfImportedPage page = copy.getImportedPage(reader, i);
                    document.newPage();
                    copy.addPage(page);
                }
            }
            document.close();
        } finally {
            clean();
        }
    }

    private static void clean() {
        for (Path path : toDeleteFilePaths) {
            org.apache.commons.io.FileUtils.deleteQuietly(path.toFile());
        }
    }

    /**
     * 获取图片、Word 和 PDF 的路径
     *
     * @param dirAbsPath 输入目录
     * @return FilePath 对象，包含图片和PDF路径
     */
    private static FilePath getPaths(Path dirAbsPath) {
        List<Path> paths;
        FilePath path = new FilePath();
        try {
            paths = Files.walk(dirAbsPath, 1)
                    .filter(Files::isRegularFile)
                    .filter(e -> SUFFIX_LIST.contains(getSuffix(e)))
                    .filter(e -> !e.getFileName().toString().contains("merge"))
                    .collect(Collectors.toList());
            LogUtils.print("文件路径：" + paths);
        } catch (IOException e) {
            e.printStackTrace();
            LogUtils.error(e.getMessage());
            return path;
        }

        // 获取图片的路径并转换为 PDF
        Map<String, Image> imageMap = getImageMap(paths);
        List<Path> imageToPdfPaths = imageToPdf(imageMap, dirAbsPath);

        // 处理 docx 转 PDF
        List<Path> docxPaths = paths.stream()
                .filter(e -> DOCX_SUFFIX.equals(getSuffix(e)))
                .collect(Collectors.toList());
        List<Path> docxToPdfPaths = convertDocxToPdf(docxPaths, dirAbsPath);
        toDeleteFilePaths.addAll(docxToPdfPaths);

        // 过滤 PDF 文件
        List<Path> pdfPaths = paths.stream()
                .filter(e -> PDF_SUFFIX.equals(getSuffix(e)))
                .collect(Collectors.toList());
        pdfPaths.addAll(imageToPdfPaths);
        pdfPaths.addAll(docxToPdfPaths);

        // 按文件名排序
        pdfPaths = pdfPaths.stream()
                .sorted(Comparator.comparing(Path::getFileName))
                .collect(Collectors.toList());

        path.setImagePaths(imageToPdfPaths);
        path.setPdfPaths(pdfPaths);
        return path;
    }

    /**
     * 获取文件后缀
     */
    private static String getSuffix(Path e) {
        String filename = e.getFileName().toString();
        int lastDotIndex = filename.lastIndexOf(".");
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 获取图片名称和图片
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
                image.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
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

    /**
     * Word(docx) 转 PDF
     */
    private static List<Path> convertDocxToPdf(List<Path> docxPaths, Path dirAbsPath) {
        List<Path> pdfPaths = new ArrayList<>();
        for (Path docxPath : docxPaths) {
            try {
                String pdfPath = docxPath.toString().replace(".docx", ".pdf");
//                WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(docxPath.toFile());
//                FileOutputStream fos = new FileOutputStream(pdfPath);
//                PdfSettings pdfSettings = new PdfSettings();
//                PdfConverter.getInstance().convert(wordMLPackage, fos, pdfSettings);
//                fos.close();
                String docxPathStr = docxPath.toAbsolutePath().toString();
                LogUtils.print("docxPath:" + docxPathStr);
                WordUtils.convertDocxToPdf(docxPathStr, pdfPath);
                pdfPaths.add(Paths.get(pdfPath));
            } catch (IOException | Docx4JException e) {
                e.printStackTrace();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return pdfPaths;
    }
}
