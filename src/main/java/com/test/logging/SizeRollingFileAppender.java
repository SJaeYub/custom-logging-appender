package com.test.logging;

import org.apache.log4j.FileAppender;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;

public class SizeRollingFileAppender extends FileAppender {

    private long maxFileSize = 10 * 1024 * 1024; // 기본 최대 파일 크기: 10MB
    private int maxBackupIndex = 5; // 기본 백업 파일 수
    private String datePattern = "'_'yyyyMMdd_HH"; // 기본 날짜 패턴
    private String currentFilePattern = "'_'yyyyMMdd"; // 현재 로그 파일 날짜 패턴
    private SimpleDateFormat sdf;
    private SimpleDateFormat currentSdf;
    private SimpleDateFormat sizeExceededSdf; // maxSize 초과 시 사용할 포맷
    private String scheduledFilename;
    private long nextRollover = 0;
    private Date now = new Date();
    private Date nextCheck = new Date();
    private RollingCalendar rc = new RollingCalendar();
    private String originalFileName;

    public SizeRollingFileAppender() {
        super();
    }

    public void setMaxFileSize(String value) {
        maxFileSize = OptionConverter.toFileSize(value, maxFileSize + 1);
        nextRollover = maxFileSize;
    }

    public void setMaxBackupIndex(int maxBackups) {
        this.maxBackupIndex = maxBackups;
    }

    public void setDatePattern(String pattern) {
        datePattern = pattern;
        rc.setDatePattern(datePattern);
    }

    @Override
    public void setFile(String file) {
        this.originalFileName = file;
        this.fileName = file;  // 원본 파일 이름 유지
    }

    @Override
    public void activateOptions() {
        if (originalFileName != null) {
            try {
                now = new Date();
                sdf = new SimpleDateFormat(datePattern);
                currentSdf = new SimpleDateFormat(currentFilePattern);
                sizeExceededSdf = new SimpleDateFormat("yyyyMMdd"); // maxSize 초과 시 YYYYMMDD 포맷
                rc.setDatePattern(datePattern);
                scheduledFilename = generateCurrentFilename(now);

                // 실제 파일 생성
                setFile(scheduledFilename, false, bufferedIO, bufferSize);

                nextCheck = rc.getNextCheckDate(now);
                nextRollover = maxFileSize;
            } catch (IOException e) {
                LogLog.error("Failed to create log file: " + scheduledFilename, e);
                throw new RuntimeException("Failed to create log file", e);
            }
        }
        super.activateOptions();
    }

    @Override
    protected void subAppend(LoggingEvent event) {
        long n = System.currentTimeMillis();
        if (n >= nextCheck.getTime()) {
            now = new Date(n);
            nextCheck = rc.getNextCheckDate(now);
            rollOverTime();
        }

        super.subAppend(event);
        if (fileName != null && qw != null) {
            long size = ((File) new File(fileName)).length();
            if (size >= nextRollover) {
                rollOverSize();
            }
        }
    }

    private synchronized void rollOverTime() {
        String newFilename = generateCurrentFilename(now);
        if (!scheduledFilename.equals(newFilename)) {
            closeFile();

            // 기존 파일을 백업하고, 새로운 파일을 생성
            File file = new File(scheduledFilename);
            if (file.exists()) {
                String backupFilename = generateBackupFilenameForTimeChange(scheduledFilename, 1);
                File backupFile = new File(backupFilename);
                boolean renameSucceeded = file.renameTo(backupFile);
                if (!renameSucceeded) {
                    LogLog.warn("Failed to rename [" + scheduledFilename + "] to [" + backupFile.getPath() + "].");
                }
            }

            // 기존 백업 파일의 이름을 변경
            for (int i = maxBackupIndex; i > 0; i--) {
                File existingBackup = new File(generateBackupFilenameForSizeExceeded(scheduledFilename, i));
                if (existingBackup.exists()) {
                    String newBackupFilename = generateBackupFilenameForTimeChange(scheduledFilename, i);
                    File newBackupFile = new File(newBackupFilename);
                    existingBackup.renameTo(newBackupFile);
                }
            }

            scheduledFilename = newFilename;
            try {
                setFile(scheduledFilename, false, bufferedIO, bufferSize);
            } catch (IOException e) {
                LogLog.error("setFile(" + scheduledFilename + ", false) call failed.", e);
            }
        }
    }

    private synchronized void rollOverSize() {
        if (qw == null) {
            LogLog.warn("No output stream. Rollover failed.");
            return;
        }

        closeFile();

        // Find the next available index for the backup file
        int nextIndex = 1;
        File existingBackup;
        while ((existingBackup = new File(generateBackupFilenameForSizeExceeded(scheduledFilename, nextIndex))).exists()) {
            nextIndex++;
        }

        // Rename the current log file to the next available index
        File target = new File(generateBackupFilenameForSizeExceeded(scheduledFilename, nextIndex));
        File file = new File(scheduledFilename);
        boolean renameSucceeded = file.renameTo(target);

        if (!renameSucceeded) {
            LogLog.warn("Failed to rename [" + scheduledFilename + "] to [" + target.getPath() + "].");
        }

        try {
            setFile(scheduledFilename, false, bufferedIO, bufferSize);
        } catch (IOException e) {
            LogLog.error("setFile(" + scheduledFilename + ", false) call failed.", e);
        }
        nextRollover = maxFileSize;
    }

    private String generateCurrentFilename(Date date) {
        String baseFilename = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        return baseFilename + currentSdf.format(date) + ".log";
    }

    private String generateBackupFilenameForSizeExceeded(String baseFilename, int index) {
        String backupDate = sizeExceededSdf.format(now);
        return baseFilename.substring(0, baseFilename.lastIndexOf('_')) + "_" + backupDate + "." + index + ".log";
    }

    private String generateBackupFilenameForTimeChange(String baseFilename, int index) {
        String backupDate = sdf.format(now);
        String[] parts = backupDate.split("_");
        String datePart = parts[0];
        String timePart = parts[1];
        return baseFilename.substring(0, baseFilename.lastIndexOf('_')) + "_" + datePart + "_" + timePart + "." + index + ".log";
    }

    private class RollingCalendar extends Calendar {
        private SimpleDateFormat sdf;

        RollingCalendar() {
            super();
        }

        void setDatePattern(String pattern) {
            sdf = new SimpleDateFormat(pattern);
        }

        public Date getNextCheckDate(Date now) {
            if (sdf.toPattern().contains("HH")) {
                return getNextDateAtHour(now);
            } else {
                return getNextDateAtMidnight(now);
            }
        }

        private Date getNextDateAtMidnight(Date now) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.DATE, 1);
            return cal.getTime();
        }

        private Date getNextDateAtHour(Date now) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(now);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            cal.add(Calendar.HOUR_OF_DAY, 1);
            return cal.getTime();
        }

        @Override
        protected void computeTime() {}
        @Override
        protected void computeFields() {}
        @Override
        public void add(int field, int amount) {}
        @Override
        public void roll(int field, boolean up) {}
        @Override
        public int getMinimum(int field) { return 0; }
        @Override
        public int getMaximum(int field) { return 0; }
        @Override
        public int getGreatestMinimum(int field) { return 0; }
        @Override
        public int getLeastMaximum(int field) { return 0; }
    }
}
