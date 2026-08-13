package com.nemal.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ExcelHelper {

    public static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final int TEMPLATE_ROW_COUNT = 500;
    /** Excel explicit-list validation is limited to 255 characters. */
    private static final int EXPLICIT_LIST_MAX_CHARS = 250;
    private static final DataFormatter FORMATTER = new DataFormatter();

    private ExcelHelper() {
    }

    public static void validateExcelFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx")) {
            throw new IllegalArgumentException("Only .xlsx Excel files are supported");
        }
    }

    public static byte[] writeSheet(String sheetName, List<String> headers, List<List<Object>> rows) {
        return writeSheet(sheetName, headers, rows, Map.of());
    }

    /**
     * Writes a data sheet plus optional in-cell dropdowns.
     * Small lists are stored on the field itself. Longer lists use hidden
     * columns on the same sheet (no separate Lookups tab).
     */
    public static byte[] writeSheet(
            String sheetName,
            List<String> headers,
            List<List<Object>> rows,
            Map<String, List<String>> dropdownsByHeader
    ) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String dataSheetName = sheetName == null || sheetName.isBlank() ? "Data" : sheetName;
            Sheet sheet = workbook.createSheet(dataSheetName);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                headerRow.createCell(i).setCellValue(headers.get(i));
            }

            int rowIdx = 1;
            for (List<Object> rowValues : rows) {
                Row row = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.size(); i++) {
                    Object value = i < rowValues.size() ? rowValues.get(i) : null;
                    setCellValue(row.createCell(i), value);
                }
            }

            Map<String, List<String>> dropdowns = dropdownsByHeader == null ? Map.of() : dropdownsByHeader;
            if (!dropdowns.isEmpty()) {
                addInSheetDropdowns(workbook, sheet, dataSheetName, headers, dropdowns);
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write Excel file", e);
        }
    }

    public static List<Map<String, String>> readRows(InputStream inputStream) {
        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
            if (sheet == null) {
                return List.of();
            }
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            short lastCell = headerRow.getLastCellNum();
            for (int i = 0; i < lastCell; i++) {
                headers.add(normalizeHeader(cellString(headerRow.getCell(i))));
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isBlankRow(row, headers.size())) {
                    continue;
                }
                Map<String, String> map = new LinkedHashMap<>();
                boolean anyValue = false;
                for (int c = 0; c < headers.size(); c++) {
                    String header = headers.get(c);
                    if (header.isBlank()) {
                        continue;
                    }
                    String value = cellString(row.getCell(c));
                    if (!value.isBlank()) {
                        anyValue = true;
                    }
                    map.put(header, value);
                }
                if (anyValue) {
                    rows.add(map);
                }
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid or corrupted Excel file", e);
        }
    }

    public static ResponseEntity<byte[]> downloadResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
                .body(bytes);
    }

    public static String get(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String normalized = normalizeHeader(key);
            for (Map.Entry<String, String> entry : row.entrySet()) {
                if (normalizeHeader(entry.getKey()).equals(normalized)) {
                    return normalizeLookup(entry.getValue());
                }
            }
        }
        return "";
    }

    public static Integer getInteger(Map<String, String> row, String... keys) {
        String raw = get(row, keys);
        if (raw.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(raw.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number: " + raw);
        }
    }

    public static Boolean getBoolean(Map<String, String> row, String... keys) {
        String raw = get(row, keys);
        if (raw.isBlank()) {
            return null;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (List.of("true", "yes", "y", "1").contains(value)) {
            return true;
        }
        if (List.of("false", "no", "n", "0").contains(value)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean: " + raw);
    }

    public static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        String[] parts = raw.split("[,;|]");
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isBlank()) {
                values.add(trimmed);
            }
        }
        return values;
    }

    public static String joinList(Iterable<?> values) {
        if (values == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Object value : values) {
            if (value != null && !String.valueOf(value).isBlank()) {
                parts.add(String.valueOf(value).trim());
            }
        }
        return String.join("; ", parts);
    }

    public static String normalizeLookup(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00A0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202F', ' ')
                .trim()
                .replaceAll("\\s+", " ");
    }

    public static boolean lookupEquals(String left, String right) {
        return normalizeLookup(left).equalsIgnoreCase(normalizeLookup(right));
    }

    /** Prefer unique values while preserving order. */
    public static List<String> uniqueNonBlank(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (trimmed.isBlank() || !seen.add(trimmed.toLowerCase(Locale.ROOT))) {
                continue;
            }
            result.add(trimmed);
        }
        return result;
    }

    private static void addInSheetDropdowns(
            Workbook workbook,
            Sheet dataSheet,
            String sheetName,
            List<String> headers,
            Map<String, List<String>> dropdownsByHeader
    ) {
        DataValidationHelper helper = dataSheet.getDataValidationHelper();
        int hiddenCol = headers.size() + 1;

        for (Map.Entry<String, List<String>> entry : dropdownsByHeader.entrySet()) {
            String header = entry.getKey();
            List<String> values = uniqueNonBlank(entry.getValue());
            if (values.isEmpty()) {
                continue;
            }

            int headerIndex = headers.indexOf(header);
            if (headerIndex < 0) {
                continue;
            }

            DataValidationConstraint constraint;
            if (canUseExplicitList(values)) {
                constraint = helper.createExplicitListConstraint(values.toArray(String[]::new));
            } else {
                for (int i = 0; i < values.size(); i++) {
                    int rowIndex = i + 1;
                    Row row = dataSheet.getRow(rowIndex);
                    if (row == null) {
                        row = dataSheet.createRow(rowIndex);
                    }
                    row.createCell(hiddenCol).setCellValue(values.get(i));
                }
                dataSheet.setColumnHidden(hiddenCol, true);

                String namedRange = sanitizeNamedRange(header + "_" + hiddenCol);
                String colLetter = columnLetter(hiddenCol);
                String quotedSheet = "'" + sheetName.replace("'", "''") + "'";
                String formula = quotedSheet + "!$" + colLetter + "$2:$" + colLetter + "$" + (values.size() + 1);

                Name name = workbook.createName();
                name.setNameName(namedRange);
                name.setRefersToFormula(formula);
                constraint = helper.createFormulaListConstraint(namedRange);
                hiddenCol++;
            }

            CellRangeAddressList addressList = new CellRangeAddressList(1, TEMPLATE_ROW_COUNT, headerIndex, headerIndex);
            DataValidation validation = helper.createValidation(constraint, addressList);
            validation.setSuppressDropDownArrow(true);
            validation.setShowErrorBox(false); // allow typing (e.g. multi domainCodes)
            validation.setEmptyCellAllowed(true);
            dataSheet.addValidationData(validation);
        }
    }

    private static boolean canUseExplicitList(List<String> values) {
        int length = 0;
        for (int i = 0; i < values.size(); i++) {
            String value = values.get(i);
            if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
                return false;
            }
            length += value.length() + (i == 0 ? 0 : 1);
            if (length > EXPLICIT_LIST_MAX_CHARS) {
                return false;
            }
        }
        return true;
    }

    private static String sanitizeNamedRange(String raw) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9_]", "_");
        if (cleaned.isBlank() || Character.isDigit(cleaned.charAt(0))) {
            cleaned = "L_" + cleaned;
        }
        return cleaned.length() > 250 ? cleaned.substring(0, 250) : cleaned;
    }

    private static String columnLetter(int index) {
        int n = index + 1;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
            return;
        }
        if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
            return;
        }
        if (value instanceof Boolean bool) {
            cell.setCellValue(bool);
            return;
        }
        cell.setCellValue(String.valueOf(value));
    }

    private static String cellString(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return "";
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    private static boolean isBlankRow(Row row, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            if (!cellString(row.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeHeader(String header) {
        if (header == null) {
            return "";
        }
        return header.trim().toLowerCase(Locale.ROOT)
                .replace('*', ' ')
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replace(' ', '_');
    }
}
