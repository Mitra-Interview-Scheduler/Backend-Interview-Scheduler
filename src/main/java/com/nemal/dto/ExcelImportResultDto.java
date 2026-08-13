package com.nemal.dto;

import java.util.ArrayList;
import java.util.List;

public record ExcelImportResultDto(
        int created,
        int skipped,
        int failed,
        List<String> errors,
        List<String> messages
) {
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int created;
        private int skipped;
        private int failed;
        private final List<String> errors = new ArrayList<>();
        private final List<String> messages = new ArrayList<>();

        public void created() {
            created++;
        }

        public void skipped(String message) {
            skipped++;
            if (message != null && !message.isBlank()) {
                messages.add(message);
            }
        }

        public void failed(String message) {
            failed++;
            if (message != null && !message.isBlank()) {
                errors.add(message);
            }
        }

        public ExcelImportResultDto build() {
            return new ExcelImportResultDto(created, skipped, failed, List.copyOf(errors), List.copyOf(messages));
        }
    }
}
