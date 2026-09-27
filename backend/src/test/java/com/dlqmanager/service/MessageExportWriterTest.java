package com.dlqmanager.service;

import com.dlqmanager.model.dto.DlqMessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;

class MessageExportWriterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MessageExportWriter writer = new MessageExportWriter(objectMapper);

    @Test
    void plainValuesAreLeftAsIs() {
        assertThat(MessageExportWriter.csvCell("DB Connection Timeout")).isEqualTo("DB Connection Timeout");
        assertThat(MessageExportWriter.csvCell(null)).isEmpty();
    }

    @Test
    void valuesWithCommasQuotesOrNewlinesAreQuoted() {
        assertThat(MessageExportWriter.csvCell("a,b")).isEqualTo("\"a,b\"");
        assertThat(MessageExportWriter.csvCell("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(MessageExportWriter.csvCell("line1\nline2")).isEqualTo("\"line1\nline2\"");
    }

    @Test
    void formulaLikeValuesAreNeutralised() {
        // Would otherwise run as a formula when the CSV is opened in Excel / Google Sheets
        assertThat(MessageExportWriter.csvCell("=HYPERLINK(\"http://evil\")")).startsWith("\"'=");
        assertThat(MessageExportWriter.csvCell("+1")).isEqualTo("'+1");
        assertThat(MessageExportWriter.csvCell("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }

    @Test
    void writesHeaderAndOneRowPerMessage() throws Exception {
        StringWriter out = new StringWriter();

        writer.writeCsvHeader(out);
        writer.writeCsvRow(out, message());

        String[] lines = out.toString().split("\r\n");
        assertThat(lines[0]).isEqualTo("﻿" + String.join(",", MessageExportWriter.CSV_COLUMNS));
        assertThat(lines[1]).startsWith("2,41,ORD-1,2026-01-01T00:00:00Z,\"Payment gateway timeout, retry later\"")
                .contains(",false,,")
                .endsWith("\"{\"\"orderId\"\":\"\"ORD-1\"\"}\"");
    }

    @Test
    void writesValidJsonArray() throws Exception {
        StringWriter out = new StringWriter();

        writer.writeJsonStart(out);
        writer.writeJsonItem(out, message(), true);
        writer.writeJsonItem(out, message(), false);
        writer.writeJsonEnd(out);

        JsonNode array = objectMapper.readTree(out.toString());
        assertThat(array.isArray()).isTrue();
        assertThat(array).hasSize(2);
        assertThat(array.get(0).get("messageKey").asText()).isEqualTo("ORD-1");
        assertThat(array.get(0).get("payload").get("orderId").asText()).isEqualTo("ORD-1");
    }

    private DlqMessageDto message() throws Exception {
        DlqMessageDto dto = new DlqMessageDto();
        dto.setPartition(2);
        dto.setOffset(41L);
        dto.setMessageKey("ORD-1");
        dto.setTimestamp("2026-01-01T00:00:00Z");
        dto.setErrorMessage("Payment gateway timeout, retry later");
        dto.setPayload(objectMapper.readTree("{\"orderId\":\"ORD-1\"}"));
        return dto;
    }
}
