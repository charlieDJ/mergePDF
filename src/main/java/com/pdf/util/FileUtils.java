package com.pdf.util;

import java.io.File;
import java.io.IOException;

public class FileUtils {

    public static String getCurrentDir() {
        File directory = new File(".");
        String dir = "";
        try {
            dir = directory.getCanonicalPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return dir;
    }
}
