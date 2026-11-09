package com.example.email;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeUtility;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Properties;

final class MimeEmailParser {

    private static final Session SESSION = Session.getInstance(new Properties());

    private MimeEmailParser() {
    }

    static Email parse(InputStream rawMessage) {
        MimeMessage message;
        try {
            message = new MimeMessage(SESSION, rawMessage);
        } catch (MessagingException e) {
            if (e.getNextException() instanceof IOException ioException) {
                throw new UncheckedIOException(ioException);
            }
            return new Email(null, null, null, null);
        }
        return new Email(from(message), title(message), content(message), sentAt(message));
    }

    private static String from(MimeMessage message) {
        try {
            String header = message.getHeader("From", ", ");
            return header == null ? null : decode(MimeUtility.unfold(header));
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String decode(String text) {
        try {
            return MimeUtility.decodeText(text);
        } catch (UnsupportedEncodingException e) {
            return text;
        }
    }

    private static String title(MimeMessage message) {
        try {
            return message.getSubject();
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String sentAt(MimeMessage message) {
        try {
            Date date = message.getSentDate();
            return date == null ? null : date.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
        } catch (MessagingException e) {
            return null;
        }
    }

    private static String content(MimeMessage message) {
        String plainText = findBody(message, "text/plain");
        return plainText != null ? plainText : findBody(message, "text/html");
    }

    private static String findBody(Part part, String mimeType) {
        try {
            if (part.isMimeType("multipart/*")) {
                Multipart multipart = (Multipart) part.getContent();
                for (int i = 0; i < multipart.getCount(); i++) {
                    BodyPart child = multipart.getBodyPart(i);
                    String body = findBody(child, mimeType);
                    if (body != null) {
                        return body;
                    }
                }
                return null;
            }
            if (isAttachment(part) || !part.isMimeType(mimeType)) {
                return null;
            }
            return part.getContent() instanceof String text ? text : null;
        } catch (MessagingException | IOException e) {
            return null;
        }
    }

    private static boolean isAttachment(Part part) throws MessagingException {
        return Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || part.getFileName() != null;
    }
}
