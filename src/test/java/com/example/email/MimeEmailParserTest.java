package com.example.email;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class MimeEmailParserTest {

    @Test
    void readsHeadersAndConvertsDateToUtc() {
        Email email = parse("""
            From: Jane Doe <jane@example.com>\r
            Subject: Hello\r
            Date: Mon, 9 Nov 2026 12:16:06 +1100\r
            \r
            Hi there.\r
            """);

        assertEquals("Jane Doe <jane@example.com>", email.from());
        assertEquals("Hello", email.title());
        assertEquals("Hi there.", email.content().strip());
        assertEquals("2026-11-09T01:16:06Z", email.sentAt());
    }

    @Test
    void decodesEncodedHeadersAndBase64Body() {
        Email email = parse("""
            From: =?UTF-8?B?SMOpbGVuZQ==?= <helene@example.com>\r
            Subject: =?UTF-8?B?SMOpbGxv?=\r
            Content-Type: text/plain; charset=UTF-8\r
            Content-Transfer-Encoding: base64\r
            \r
            SMOpbGxv\r
            """);

        assertEquals("Hélene <helene@example.com>", email.from());
        assertEquals("Héllo", email.title());
        assertEquals("Héllo", email.content().strip());
    }

    @Test
    void decodesQuotedPrintableBodyInNonUtf8Charset() {
        Email email = parse("""
            Content-Type: text/plain; charset=ISO-8859-1\r
            Content-Transfer-Encoding: quoted-printable\r
            \r
            caf=E9\r
            """);

        assertEquals("café", email.content().strip());
    }

    @Test
    void prefersPlainTextOverHtml() {
        Email email = parse("""
            Content-Type: multipart/alternative; boundary="b"\r
            \r
            --b\r
            Content-Type: text/html; charset=UTF-8\r
            \r
            <p>Rich</p>\r
            --b\r
            Content-Type: text/plain; charset=UTF-8\r
            \r
            Plain\r
            --b--\r
            """);

        assertEquals("Plain", email.content().strip());
    }

    @Test
    void fallsBackToHtmlWhenThereIsNoPlainText() {
        Email email = parse("""
            Content-Type: text/html; charset=UTF-8\r
            \r
            <p>Rich</p>\r
            """);

        assertEquals("<p>Rich</p>", email.content().strip());
    }

    @Test
    void returnsBodyFromNestedMultipartWithoutAttachmentData() {
        Email email = parse("""
            Content-Type: multipart/mixed; boundary="outer"\r
            \r
            --outer\r
            Content-Type: text/plain; charset=UTF-8\r
            Content-Disposition: attachment; filename="notes.txt"\r
            \r
            ATTACHED-TEXT\r
            --outer\r
            Content-Type: multipart/alternative; boundary="inner"\r
            \r
            --inner\r
            Content-Type: text/plain; charset=UTF-8\r
            \r
            The body\r
            --inner\r
            Content-Type: text/html; charset=UTF-8\r
            \r
            <p>The body</p>\r
            --inner--\r
            --outer\r
            Content-Type: application/pdf\r
            Content-Disposition: attachment; filename="file.pdf"\r
            Content-Transfer-Encoding: base64\r
            \r
            QVRUQUNI\r
            --outer--\r
            """);

        assertEquals("The body", email.content().strip());
        assertFalse(email.content().contains("ATTACHED-TEXT"));
    }

    @Test
    void missingFieldsAreNull() {
        Email email = parse("X-Other: value\r\n\r\n");

        assertNull(email.from());
        assertNull(email.title());
        assertNull(email.sentAt());
    }

    @Test
    void invalidDateIsNullAndOtherFieldsArePresent() {
        Email email = parse("From: a@example.com\r\nSubject: Bad date\r\nDate: not a date\r\n\r\nBody\r\n");

        assertNull(email.sentAt());
        assertEquals("a@example.com", email.from());
        assertEquals("Bad date", email.title());
        assertEquals("Body", email.content().strip());
    }

    @Test
    void emailWithOnlyAnAttachmentHasNullContent() {
        Email email = parse("""
            Content-Type: multipart/mixed; boundary="b"\r
            \r
            --b\r
            Content-Type: application/pdf\r
            Content-Disposition: attachment; filename="file.pdf"\r
            Content-Transfer-Encoding: base64\r
            \r
            QVRUQUNI\r
            --b--\r
            """);

        assertNull(email.content());
    }

    private static Email parse(String message) {
        return MimeEmailParser.parse(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
    }
}
