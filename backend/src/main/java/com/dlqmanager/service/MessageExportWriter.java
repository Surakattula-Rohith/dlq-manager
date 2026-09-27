package com.dlqmanager.service;

import com.dlqmanager.model.dto.DlqMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.List;

/**
 * Writes DLQ messages as CSV or JSON for download
 *
 * Messages are written one at a time, so an export never holds the whole DLQ in memory.
 */
@Component
@RequiredArgsConstructor
public class MessageExportWriter {

    public static final List<String> CSV_COLUMNS = List.of(
            "partition", "offset", "key", "timestamp", "error", "exception_class", "original_topic",
            "retry_count", "failed_timestamp", "replayed", "replayed_at", "payload"
    );

    private static final String CSV_LINE_END = "\r\n";

    private final ObjectMapper objectMapper;

    // --- CSV ---

    public void writeCsvHeader(Writer out) throws IOException {
        // Byte order mark, so Excel opens the file as UTF-8 instead of mangling non-English characters
        out.write('﻿');
        out.write(String.join(",", CSV_COLUMNS));
        out.write(CSV_LINE_END);
    }

    public void writeCsvRow(Writer out, DlqMessageDto message) throws IOException {
        List<String> cells = Arrays.asList(
                str(message.getPartition()),
                str(message.getOffset()),
                message.getMessageKey(),
                message.getTimestamp(),
                message.getErrorMessage(),
                message.getExceptionClass(),
                message.getOriginalTopic(),
                str(message.getRetryCount()),
                message.getFailedTimestamp(),
                String.valueOf(message.isReplayed()),
                message.getReplayedAt(),
                message.getPayload() != null ? message.getPayload().toString() : null
        );

        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(csvCell(cells.get(i)));
        }
        out.write(line.toString());
        out.write(CSV_LINE_END);
    }

    /**
     * Make a value safe for one CSV cell
     *
     * - Quotes values containing commas, quotes or line breaks (RFC 4180)
     * - Prefixes values starting with = + - @ with a single quote, so spreadsheet apps
     *   show them as text instead of running them as formulas (CSV injection)
     */
    static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String cell = value;
        if (!cell.isEmpty() && "=+-@\t\r".indexOf(cell.charAt(0)) >= 0) {
            cell = "'" + cell;
        }
        if (cell.contains(",") || cell.contains("\"") || cell.contains("\n") || cell.contains("\r")) {
            cell = "\"" + cell.replace("\"", "\"\"") + "\"";
        }
        return cell;
    }

    // --- JSON (an array, written element by element) ---

    public void writeJsonStart(Writer out) throws IOException {
        out.write("[");
    }

    public void writeJsonItem(Writer out, DlqMessageDto message, boolean first) throws IOException {
        if (!first) {
            out.write(",");
        }
        out.write(objectMapper.writeValueAsString(message));
    }

    public void writeJsonEnd(Writer out) throws IOException {
        out.write("]");
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
