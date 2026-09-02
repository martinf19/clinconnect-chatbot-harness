package com.clinconnect.chatbot.seed;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, dependency-free reader for the hand-edited seed-data files in
 * {@code config/seed-data/*.csv}. Supports double-quoted fields (with
 * {@code ""} as an escaped quote) so text containing commas/quotes
 * round-trips correctly; blank lines and lines starting with {@code #} are
 * skipped, so a seed file can carry its own inline comments.
 *
 * <p>Hand-rolled rather than a dependency (e.g. Commons CSV/OpenCSV): this
 * project avoids adding a new library unless a phase explicitly requires
 * it (planning/PHASE-STATUS.md Phase 3 deviation #2 applies the same
 * reasoning to {@code RestClient}), and this repo's seed data is simple
 * enough (no newlines inside fields) not to need a full RFC 4180 parser.
 */
final class CsvReader {

    private CsvReader() {
    }

    /** Each row is a header-name -> raw (trimmed-by-caller) cell value map, in file order. */
    static List<Map<String, String>> read(Path path) {
        if (!Files.isReadable(path)) {
            throw new SeedDataException("Seed data file not found or not readable: " + path.toAbsolutePath());
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read seed data file: " + path, e);
        }

        List<String> header = null;
        List<Map<String, String>> rows = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) {
                continue;
            }
            List<String> cells = parseLine(line);
            if (header == null) {
                header = cells;
                continue;
            }
            if (cells.size() != header.size()) {
                throw new SeedDataException(
                        "Row has " + cells.size() + " column(s) but header has " + header.size() + " in " + path
                                + ": " + line);
            }
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < header.size(); i++) {
                row.put(header.get(i), cells.get(i));
            }
            rows.add(row);
        }
        if (header == null) {
            throw new SeedDataException("Seed data file has no header row: " + path);
        }
        return rows;
    }

    private static List<String> parseLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"' && current.isEmpty()) {
                inQuotes = true;
            } else if (c == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        cells.add(current.toString());
        return cells;
    }
}
