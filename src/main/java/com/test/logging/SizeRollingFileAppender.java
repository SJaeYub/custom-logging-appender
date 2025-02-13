package com.test.logging;

import org.apache.log4j.FileAppender;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.helpers.OptionConverter;
import org.apache.log4j.spi.LoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;

public class SizeRollingFileAppender extends FileAppender {

    private static final Logger log = LoggerFactory.getLogger(SizeRollingFileAppender.class);
    private long maxFileSize = 10 * 1024 * 1024; // 기본 최대 파일 크기: 10MB
    private int maxBackupIndex = 5; // 기본 백업 파일 수
    private String datePattern = "'_'yyyyMMdd_HH"; // 기본 날짜 패턴
    private SimpleDateFormat sdf;
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
                rc.setDatePattern(datePattern);
                scheduledFilename = generateFilename(now);

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
        String newFilename = generateFilename(now);
        if (!scheduledFilename.equals(newFilename)) {
            closeFile();

            // 기존 파일을 백업하지 않고, 새로운 파일을 생성
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
        while ((existingBackup = new File(generateBackupFilename(scheduledFilename, nextIndex))).exists()) {
            nextIndex++;
        }

        // 최대 인덱스를 초과하면 기존 백업 파일의 인덱스를 증가시키고, 가장 오래된 파일 삭제
        if (nextIndex > maxBackupIndex) {
            // 기존 백업 파일의 인덱스를 1씩 증가시키고, 가장 오래된 파일 삭제
            for (int i = maxBackupIndex; i > 1; i--) {
                File file = new File(generateBackupFilename(scheduledFilename, i));
                if (file.exists()) {
                    File target = new File(generateBackupFilename(scheduledFilename, i + 1));
                    file.renameTo(target);
                }
            }
            File oldestBackup = new File(generateBackupFilename(scheduledFilename, 2));
            if (oldestBackup.exists()) {
                File target = new File(generateBackupFilename(scheduledFilename, maxBackupIndex + 1));
                oldestBackup.renameTo(target);
                target.delete(); // 실제로 삭제
            }
            nextIndex = 1;
        }

        // Rename the current log file to the next available index
        File target = new File(generateBackupFilename(scheduledFilename, nextIndex));
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

    private static String getHourString(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH");
        return sdf.format(date);
    }

    private String generateFilename(Date date) {
        String hourString = getHourString(date);
        String baseFilename = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        String addHourFilename = baseFilename + sdf.format(date);
        System.out.println("Generated filename: " + addHourFilename);
        System.out.println(addHourFilename.lastIndexOf(hourString));
        baseFilename = addHourFilename.substring(0, addHourFilename.lastIndexOf(hourString));
        baseFilename = baseFilename.substring(0, baseFilename.length() - 1);
        System.out.println(baseFilename);
        return baseFilename + ".log";
    }

    private String generateBackupFilename(String baseFilename, int index) {
        return baseFilename.substring(0, baseFilename.lastIndexOf('.')) + "." + index + ".log";
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
        protected void computeTime() {
        }

        @Override
        protected void computeFields() {
        }

        @Override
        public void add(int field, int amount) {
        }

        @Override
        public void roll(int field, boolean up) {
        }

        @Override
        public int getMinimum(int field) {
            return 0;
        }

        @Override
        public int getMaximum(int field) {
            return 0;
        }

        @Override
        public int getGreatestMinimum(int field) {
            return 0;
        }

        @Override
        public int getLeastMaximum(int field) {
            return 0;
        }
    }
}
