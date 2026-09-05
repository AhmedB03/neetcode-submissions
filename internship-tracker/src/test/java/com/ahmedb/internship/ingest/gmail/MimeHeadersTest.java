package com.ahmedb.internship.ingest.gmail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MimeHeadersTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
            delimiter = '|',
            value = {
                "no-reply@greenhouse.io | | no-reply@greenhouse.io",
                "<no-reply@greenhouse.io> | | no-reply@greenhouse.io",
                "Stripe Recruiting <no-reply@stripe.com> | Stripe Recruiting | no-reply@stripe.com",
                "\"Datadog, Inc.\" <careers@datadoghq.com> | Datadog, Inc. | careers@datadoghq.com",
                "  Jane Street  <recruiting@janestreet.com>  | Jane Street | recruiting@janestreet.com",
                "NO-REPLY@Stripe.COM | | no-reply@stripe.com",
            })
    @DisplayName("From headers split into display name and lowercased address")
    void parsesSenderShapes(String header, String expectedName, String expectedAddress) {
        MimeHeaders.Sender sender = MimeHeaders.parseSender(header);

        assertThat(sender.displayName()).isEqualTo(expectedName);
        assertThat(sender.address()).isEqualTo(expectedAddress);
    }

    @Test
    @DisplayName("a malformed sender yields nulls rather than throwing")
    void malformedSendersAreTolerated() {
        assertThat(MimeHeaders.parseSender(null)).isEqualTo(new MimeHeaders.Sender(null, null));
        assertThat(MimeHeaders.parseSender("   ")).isEqualTo(new MimeHeaders.Sender(null, null));
        assertThat(MimeHeaders.parseSender("<>").address()).isNull();
        assertThat(MimeHeaders.parseSender("garbage").address()).isEqualTo("garbage");
    }

    @Test
    @DisplayName("RFC 2047 encoded words are decoded")
    void decodesEncodedWords() {
        // Base64 and quoted-printable forms of the same text.
        assertThat(MimeHeaders.decode("=?UTF-8?B?VGhhbmsgeW91IGZvciBhcHBseWluZw==?="))
                .isEqualTo("Thank you for applying");
        assertThat(MimeHeaders.decode("=?UTF-8?Q?Thank_you_for_applying?=")).isEqualTo("Thank you for applying");
        assertThat(MimeHeaders.decode("=?UTF-8?Q?caf=C3=A9?=")).isEqualTo("café");
    }

    @Test
    @DisplayName("plain text and mixed headers survive decoding untouched")
    void leavesPlainTextAlone() {
        assertThat(MimeHeaders.decode("Thank you for applying")).isEqualTo("Thank you for applying");
        assertThat(MimeHeaders.decode(null)).isNull();
        assertThat(MimeHeaders.decode("")).isEmpty();
        assertThat(MimeHeaders.decode("Re: =?UTF-8?B?U3RyaXBl?= interview"))
                .isEqualTo("Re: Stripe interview");
    }

    @Test
    @DisplayName("an undecodable word is kept verbatim rather than failing the poll")
    void undecodableWordsAreKept() {
        assertThat(MimeHeaders.decode("=?NOT-A-CHARSET?B?abc?=")).isEqualTo("=?NOT-A-CHARSET?B?abc?=");
        assertThat(MimeHeaders.decode("=?UTF-8?B?!!!not-base64!!!?=")).contains("not-base64");
    }

    @Test
    @DisplayName("an encoded display name is decoded before the address is split off")
    void decodesInsideSenderHeader() {
        MimeHeaders.Sender sender =
                MimeHeaders.parseSender("=?UTF-8?B?U3RyaXBlIFJlY3J1aXRpbmc=?= <no-reply@stripe.com>");

        assertThat(sender.displayName()).isEqualTo("Stripe Recruiting");
        assertThat(sender.address()).isEqualTo("no-reply@stripe.com");
    }
}
