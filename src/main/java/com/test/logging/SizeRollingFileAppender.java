package com.test.logging;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SizeRollingFileAppender extends AppenderSkeleton {

    private String fileName;
    private String datePattern;
    private long maxFileSize;
    private int maxBackupIndex;
    private boolean append = true;
    private FileWriter writer;
    private long nextRolloverTime = 0; // HH 단위 롤오버 시간

    public SizeRollingFileAppender() {}

    public void setFile(String fileName) {
        this.fileName = fileName;
    }

    public void setMaxFileSize(String maxFileSizeStr) {
        this.maxFileSize = parseFileSize(maxFileSizeStr);
    }

    public void setMaxBackupIndex(int maxBackupIndex) {
        this.maxBackupIndex = maxBackupIndex;
    }

    public void setDatePattern(String datePattern) {
        this.datePattern = datePattern;
    }

    private long parseFileSize(String fileSizeStr) {
        fileSizeStr = fileSizeStr.trim().toLowerCase();
        long fileSize = 0;
        if (fileSizeStr.endsWith("kb")) {
            fileSize = Long.parseLong(fileSizeStr.substring(0, fileSizeStr.length() - 2)) * 1024;
        } else if (fileSizeStr.endsWith("mb")) {
            fileSize = Long.parseLong(fileSizeStr.substring(0, fileSizeStr.length() - 2)) * 1024 * 1024;
        } else if (fileSizeStr.endsWith("gb")) {
            fileSize = Long.parseLong(fileSizeStr.substring(0, fileSizeStr.length() - 2)) * 1024 * 1024 * 1024;
        } else {
            fileSize = Long.parseLong(fileSizeStr);
        }
        return fileSize;
    }

    @Override
    public void activateOptions() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String currentDate = sdf.format(new Date(System.currentTimeMillis()));
        String instanceName = fileName.substring(0, fileName.lastIndexOf('.'));
        String newFileName = instanceName + "_" + currentDate + ".log";
        try {
            setFile(newFileName, append, false, 8192);
        } catch (IOException e) {
            LogLog.error("Error setting file.", e);
        }
    }


    public void setFile(String fileName, boolean append, boolean bufferedIO, int bufferSize) throws IOException {
        if (writer != null) {
            writer.close();
        }
        File file = new File(fileName);
        if (!append) {
            if (file.exists()) {
                file.delete();
            }
        }
        writer = new FileWriter(file, append);
    }

    @Override
    protected void append(LoggingEvent event) {
        if (writer == null) {
            LogLog.error("Output stream not set.");
            return;
        }

        // 파일 크기 체크
        if (new File(fileName).length() >= maxFileSize) {
            rollOver();
        }

        // 시간 체크
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String currentDate = sdf.format(new Date(System.currentTimeMillis()));

        // 파일 이름 형식 체크
        if (fileName.contains("_")) {
            String instanceName = fileName.substring(0, fileName.indexOf('_'));
            if (!currentDate.equals(fileName.substring(instanceName.length() + 1, instanceName.length() + 9))) {
                rollOver();
            }
        } else {
            LogLog.error("Invalid file name format.");
        }

        try {
            writer.write(this.layout.format(event));
            writer.flush();
        } catch (IOException e) {
            LogLog.error("Error writing to file.", e);
        }
    }



    private void rollOver() {
        try {
            writer.close();
        } catch (IOException e) {
            LogLog.error("Error closing file.", e);
        }

        // 파일 이름 생성
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HH");
        String newFileName = sdf.format(new Date(System.currentTimeMillis()));
        String instanceName = fileName.substring(0, fileName.lastIndexOf('_'));
        newFileName = instanceName + "_" + newFileName + ".log";

        // 파일 이름에 인덱스 추가
        int idx = 1;
        while (new File(newFileName + "." + idx).exists()) {
            idx++;
        }
        if (idx > 1) {
            newFileName += "." + idx;
        }

        // 파일 복사
        File srcFile = new File(fileName);
        File destFile = new File(newFileName);
        if (!srcFile.renameTo(destFile)) {
            LogLog.error("Failed to rename file.");
        }

        // 새로운 파일 열기
        SimpleDateFormat newSdf = new SimpleDateFormat("yyyyMMdd");
        String newCurrentDate = newSdf.format(new Date(System.currentTimeMillis()));
        String newFileNameForNewFile = instanceName + "_" + newCurrentDate + ".log";
        try {
            setFile(newFileNameForNewFile, append, false, 8192);
        } catch (IOException e) {
            LogLog.error("Error opening new file.", e);
        }
    }


    @Override
    public void close() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                LogLog.error("Error closing file.", e);
            }
        }
    }

    @Override
    public boolean requiresLayout() {
        return true;
    }
}
