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
    private int maxBackupIndex = 1; // 기본 백업 파일 수
    private String datePattern = "'_'yyyyMMdd_HH"; // 기본 날짜 패턴
    private SimpleDateFormat sdf;
    private String scheduledFilename;
    private boolean isSysErr = false;
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
        isSysErr = file.contains(".syserr");
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
        String datedFilename = generateFilename(now);
        if (!scheduledFilename.equals(datedFilename)) {
            closeFile();
            File target = new File(scheduledFilename);
            if (target.exists()) {
                target.renameTo(new File(generateBackupFilename(scheduledFilename, 1)));
            }
            scheduledFilename = datedFilename;
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

        // 기존 백업 파일 이름 변경
        for (int i = maxBackupIndex - 1; i > 0; i--) {
            File file = new File(generateBackupFilename(scheduledFilename, i));
            if (file.exists()) {
                File target = new File(generateBackupFilename(scheduledFilename, i + 1));
                file.renameTo(target);
            }
        }

        // 현재 로그 파일을 백업 파일로 이름 변경
        File target = new File(generateBackupFilename(scheduledFilename, 1));
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

    private String generateFilename(Date date) {
        String baseFilename = originalFileName.substring(0, originalFileName.lastIndexOf('.'));
        return baseFilename + sdf.format(date) + (isSysErr ? ".syserr.log" : ".log");
    }

    private String generateBackupFilename(String baseFilename, int index) {
        return baseFilename.substring(0, baseFilename.lastIndexOf('.')) + "." + index +
                (isSysErr ? ".syserr.log" : ".log");
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
