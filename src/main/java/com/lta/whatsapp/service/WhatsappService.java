package com.lta.whatsapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.net.ssl.*;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.ArrayList;
import java.util.Collections;


@Service
public class WhatsappService {

    // 🆕 1) recipient map
    // fill in your real numbers here
    private static final Map<String, List<String>> LINE_RECIPIENTS = Map.of(
            "NEL", List.of("whatsapp:+6584685816"),   // replace
            "EWL", List.of("whatsapp:+6593659816"),   // replace
            "CCL", List.of("whatsapp:+6593659816"),   // replace
            "DTL", List.of("whatsapp:+6593659816"),   // replace
            "TEL", List.of("whatsapp:+6593659816"),   // replace
            "GENERAL", List.of("whatsapp:+6593659816") // fallback
    );

    private final List<Map<String, Object>> auditLog = new CopyOnWriteArrayList<>();

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;   // e.g. whatsapp:+14155238886

    public void sendAlert(String body, List<String> recipients) {
        try {
            // trust-all for PoC
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll(), new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);

            for (String recipient : recipients) {
                // you can accept either "+65..." or "whatsapp:+65..."
                String toRaw = recipient.startsWith("whatsapp:") ? recipient : "whatsapp:" + recipient;

                // encode ONLY the plus sign, not the "whatsapp:"
                String to = encodePlus(toRaw);
                String from = encodePlus(fromNumber);

                StringBuilder form = new StringBuilder();
                form.append("From=").append(from);
                form.append("&To=").append(to);
                form.append("&Body=").append(urlEncode(body));

                String formString = form.toString();
                System.out.println("Sending to Twilio form: " + formString);

                URL url = new URL("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setRequestProperty("Authorization", basicAuth(accountSid, authToken));

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(formString.getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code >= 300) {
                    String err = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("Twilio HTTP " + code + ": " + err);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Twilio send failed (curl-style, plus-encoded): " + e.getMessage(), e);
        }
    }

    // 🆕 2) helper the controller will call
    public List<String> getRecipientsForLine(String line) {
        if (line == null) {
            return LINE_RECIPIENTS.get("GENERAL");
        }
        // normalise
        String key = line.toUpperCase();
        return LINE_RECIPIENTS.getOrDefault(key, LINE_RECIPIENTS.get("GENERAL"));
    }

    private TrustManager[] trustAll() {
        return new TrustManager[]{
                new X509TrustManager() {
                    public void checkClientTrusted(X509Certificate[] xcs, String s) { }
                    public void checkServerTrusted(X509Certificate[] xcs, String s) { }
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                }
        };
    }

    private String basicAuth(String user, String pass) {
        String s = user + ":" + pass;
        return "Basic " + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private String urlEncode(String v) {
        return URLEncoder.encode(v, StandardCharsets.UTF_8);
    }

    // this is the key: "whatsapp:+65..." -> "whatsapp:%2B65..."
    private String encodePlus(String v) {
        return v.replace("+", "%2B");
    }

        // called by controller after successful send
    public void addAuditEntry(String timestamp,
                              String line,
                              String message,
                              List<String> recipients,
                              boolean test) {
        auditLog.add(Map.of(
                "timestamp", timestamp,
                "line", line,
                "message", message,
                "recipients", recipients,
                "test", test
        ));

        // keep it small
        if (auditLog.size() > 200) {
            auditLog.remove(0);
        }
    }

    // optional: expose for a future GET endpoint
    public List<Map<String, Object>> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }

}
