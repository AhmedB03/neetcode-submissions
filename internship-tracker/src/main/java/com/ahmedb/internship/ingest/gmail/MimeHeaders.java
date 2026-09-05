package com.ahmedb.internship.ingest.gmail;

import java.nio.charset.Charset;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Header parsing helpers: RFC 2047 encoded words and RFC 5322 address fields. */
public final class MimeHeaders {

    /** {@code =?charset?B?base64?=} or {@code =?charset?Q?quoted-printable?=} */
    private static final Pattern ENCODED_WORD =
            Pattern.compile("=\\?([^?]+)\\?([BbQq])\\?([^?]*)\\?=");

    private static final Pattern ADDRESS_IN_ANGLE_BRACKETS = Pattern.compile("<([^>]*)>");

    private MimeHeaders() {}

    /**
     * Decodes RFC 2047 encoded words, which is how non-ASCII subjects and sender names arrive.
     *
     * <p>Anything that fails to decode is returned as-is: a mangled subject is a worse classifier
     * input than a raw one, but neither is worth failing a poll over.
     */
    public static String decode(String value) {
        if (value == null || value.isEmpty() || !value.contains("=?")) {
            return value;
        }
        Matcher matcher = ENCODED_WORD.matcher(value);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            // Whitespace separating two adjacent encoded words is not part of the text.
            String between = value.substring(last, matcher.start());
            if (!(out.length() > 0 && between.isBlank() && !between.isEmpty())) {
                out.append(between);
            }
            out.append(decodeWord(matcher.group(1), matcher.group(2), matcher.group(3), matcher.group()));
            last = matcher.end();
        }
        out.append(value.substring(last));
        return out.toString();
    }

    private static String decodeWord(String charsetName, String encoding, String text, String original) {
        try {
            Charset charset = Charset.forName(charsetName);
            if (encoding.equalsIgnoreCase("B")) {
                return new String(Base64.getMimeDecoder().decode(text), charset);
            }
            return decodeQuotedPrintable(text, charset);
        } catch (IllegalArgumentException e) {
            // Covers an unknown or malformed charset name and a failed base64/hex decode alike.
            // An undecodable word is returned verbatim rather than failing the poll.
            return original;
        }
    }

    private static String decodeQuotedPrintable(String text, Charset charset) {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '_') {
                bytes.write(' ');
            } else if (c == '=' && i + 2 < text.length()) {
                bytes.write(Integer.parseInt(text.substring(i + 1, i + 3), 16));
                i += 2;
            } else {
                bytes.write(c);
            }
        }
        return bytes.toString(charset);
    }

    /**
     * Splits a {@code From} header into display name and address.
     *
     * <p>Handles the shapes that actually turn up: {@code a@b.com}, {@code <a@b.com>}, {@code Name
     * <a@b.com>} and {@code "Name, Inc." <a@b.com>}. Anything unrecognisable yields an empty
     * address rather than an exception -- a malformed sender is an email that fails to match a
     * company, not a failed poll.
     */
    public static Sender parseSender(String fromHeader) {
        if (fromHeader == null || fromHeader.isBlank()) {
            return new Sender(null, null);
        }
        String header = decode(fromHeader).trim();

        Matcher angled = ADDRESS_IN_ANGLE_BRACKETS.matcher(header);
        if (angled.find()) {
            String address = normaliseAddress(angled.group(1));
            String displayName = unquote(header.substring(0, angled.start()).trim());
            return new Sender(displayName.isBlank() ? null : displayName, address);
        }

        return new Sender(null, normaliseAddress(header));
    }

    private static String normaliseAddress(String address) {
        String trimmed = address == null ? "" : address.trim().toLowerCase(Locale.ROOT);
        return trimmed.isBlank() ? null : trimmed;
    }

    private static String unquote(String value) {
        String trimmed = value.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    /** A parsed {@code From} header. Either field may be null. */
    public record Sender(String displayName, String address) {}
}
