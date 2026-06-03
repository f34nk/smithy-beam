package io.smithy.beam.test;

import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.node.ObjectNode;
import software.amazon.smithy.model.node.StringNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * SigV4 signing helper for golden-vector tests. Mirrors inputs the generated
 * SigV4 signing hook receives from client operation stubs.
 */
final class AwsSigV4TestSigner {

    private AwsSigV4TestSigner() {}

    static SignResult sign(ObjectNode vector) {
        ObjectNode credentials = vector.expectObjectMember("credentials");
        ObjectNode signing = vector.expectObjectMember("signing");
        ObjectNode request = vector.expectObjectMember("request");
        boolean unsignedPayload = vector.getBooleanMemberOrDefault("unsignedPayload", false);

        String accessKeyId = credentials.expectStringMember("accessKeyId").getValue();
        String secretAccessKey = credentials.expectStringMember("secretAccessKey").getValue();
        String region = signing.expectStringMember("region").getValue();
        String service = signing.expectStringMember("service").getValue();
        String amzDate = signing.expectStringMember("amzDate").getValue();
        String dateStamp = amzDate.substring(0, 8);

        String method = request.expectStringMember("method").getValue().toUpperCase(Locale.ROOT);
        String path = request.getStringMemberOrDefault("path", "/");
        String host = request.expectStringMember("host").getValue();
        byte[] body = request.getStringMemberOrDefault("body", "").getBytes(StandardCharsets.UTF_8);

        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        headers.put("host", host);
        headers.put("x-amz-date", amzDate);
        request.getObjectMember("headers").ifPresent(headerNode -> {
            for (Map.Entry<StringNode, Node> entry : headerNode.getMembers().entrySet()) {
                headers.put(entry.getKey().getValue(), entry.getValue().expectStringNode().getValue());
            }
        });

        String payloadHash = unsignedPayload ? "UNSIGNED-PAYLOAD" : sha256Hex(body);
        headers.put("x-amz-content-sha256", payloadHash);

        String canonicalQuery = canonicalQuery(request.getObjectMember("query").orElse(null));
        String canonicalHeaders = canonicalHeaders(headers);
        String signedHeaders = signedHeaderNames(headers);
        String canonicalRequest = String.join("\n",
                method,
                path,
                canonicalQuery,
                canonicalHeaders,
                signedHeaders,
                payloadHash);

        String credentialScope = dateStamp + "/" + region + "/" + service + "/aws4_request";
        String stringToSign = String.join("\n",
                "AWS4-HMAC-SHA256",
                amzDate,
                credentialScope,
                sha256Hex(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = deriveSigningKey(secretAccessKey, dateStamp, region, service);
        String signature = hmacHex(signingKey, stringToSign);

        Map<String, String> signedRequestHeaders = new LinkedHashMap<>(headers);
        signedRequestHeaders.put(
                "authorization",
                "AWS4-HMAC-SHA256 Credential="
                        + accessKeyId
                        + "/"
                        + credentialScope
                        + ", SignedHeaders="
                        + signedHeaders
                        + ", Signature="
                        + signature);

        return new SignResult(canonicalRequest, stringToSign, signature, signedRequestHeaders);
    }

    private static String canonicalQuery(ObjectNode queryNode) {
        if (queryNode == null || queryNode.getMembers().isEmpty()) {
            return "";
        }
        List<String> pairs = new ArrayList<>();
        queryNode.getMembers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey((a, b) -> a.getValue().compareTo(b.getValue())))
                .forEach(entry -> pairs.add(
                        urlEncode(entry.getKey().getValue())
                                + "="
                                + urlEncode(entry.getValue().expectStringNode().getValue())));
        return String.join("&", pairs);
    }

    private static String canonicalHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> entry.getKey().toLowerCase(Locale.ROOT) + ":" + trimAll(entry.getValue()) + "\n")
                .collect(Collectors.joining());
    }

    private static String signedHeaderNames(Map<String, String> headers) {
        return headers.keySet().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .sorted()
                .collect(Collectors.joining(";"));
    }

    private static String trimAll(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private static String urlEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            char c = (char) (b & 0xFF);
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.'
                    || c == '~') {
                encoded.append(c);
            } else {
                encoded.append(String.format("%%%02X", b & 0xFF));
            }
        }
        return encoded.toString();
    }

    private static byte[] deriveSigningKey(String secret, String dateStamp, String region, String service) {
        byte[] kSecret = ("AWS4" + secret).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmac(kSecret, dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, service);
        return hmac(kService, "aws4_request");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String hmacHex(byte[] key, String data) {
        return HexFormat.of().formatHex(hmac(key, data));
    }

    private static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    record SignResult(
            String canonicalRequest,
            String stringToSign,
            String signature,
            Map<String, String> signedHeaders) {}
}
