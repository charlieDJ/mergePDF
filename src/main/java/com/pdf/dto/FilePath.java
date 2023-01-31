package com.pdf.dto;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FilePath {
    public FilePath() {
        pdfPaths = new ArrayList<>();
        imagePaths = new ArrayList<>();
    }

    private List<Path> pdfPaths;
    private List<Path> imagePaths;

    public List<Path> getPdfPaths() {
        return pdfPaths;
    }

    public void setPdfPaths(List<Path> pdfPaths) {
        this.pdfPaths = pdfPaths;
    }

    public List<Path> getImagePaths() {
        return imagePaths;
    }

    public void setImagePaths(List<Path> imagePaths) {
        this.imagePaths = imagePaths;
    }
}
