package com.lta.whatsapp.service;

import com.lta.whatsapp.model.WhatsappSubscription;
import com.lta.whatsapp.repo.WhatsappSubscriptionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.*;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Service
public class WhatsappService {

    // in-memory fallback
    private final Map<String, Set<String>> subscribersByLine = new ConcurrentHashMap<>();

    private static final Set<String> VALID_LINES = Set.of(
        "NSL", "EWL", "NEL", "CCL", "DTL", "TEL", "BPLRT", "SPLRT"
    );

    // DB repo
    private final WhatsappSubscriptionRepository subscriptionRepository;

    // // keep your hardcoded recipients
    // private static final Map<String, List<String>> LINE_RECIPIENTS = Map.of(
    //         "NEL", List.of("whatsapp:+6584685816"),
    //         "EWL", List.of("whatsapp:+6593659816"),
    //         "CCL", List.of("whatsapp:+6593659816"),
    //         "DTL", List.of("whatsapp:+6593659816"),
    //         "TEL", List.of("whatsapp:+6593659816"),
    //         "GENERAL", List.of("whatsapp:+6593659816")
    // );

    private final List<Map<String, Object>> auditLog = new CopyOnWriteArrayList<>();

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.from-number}")
    private String fromNumber;

    public WhatsappService(WhatsappSubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }

    /* =================== SUBSCRIBE =================== */

    @Transactional
    public void subscribe(String line, String phone) {
        String normLine = (line == null || line.isBlank()) ? "GENERAL" : line.toUpperCase();
        if (!VALID_LINES.contains(normLine)) throw new IllegalArgumentException("Invalid line: " + normLine);

        String normPhone = normalizePhone(phone);

        // ✅ DB idempotent upsert
        subscriptionRepository.findByLineCodeAndPhone(normLine, normPhone)
            .orElseGet(() -> subscriptionRepository.save(new WhatsappSubscription(normLine, normPhone)));

        // keep in-memory cache in sync (optional)
        subscribersByLine.computeIfAbsent(normLine, k -> Collections.synchronizedSet(new HashSet<>())).add(normPhone);

        System.out.println("[SUB→DB] " + normLine + " / " + normPhone);
    }


    @Transactional
    public void unsubscribe(String line, String phone) {
        String normLine = (line == null || line.isBlank()) ? "GENERAL" : line.toUpperCase();
        if (!VALID_LINES.contains(normLine)) throw new IllegalArgumentException("Invalid line: " + normLine);

        String normPhone = normalizePhone(phone);

        // ✅ DB delete (idempotent)
        subscriptionRepository.deleteByLineCodeAndPhone(normLine, normPhone);

        // update cache
        Set<String> set = subscribersByLine.get(normLine);
        if (set != null) set.remove(normPhone);

        System.out.println("[UNSUB→DB] " + normLine + " / " + normPhone);
    }
    
    /* =================== READERS =================== */

    public List<String> getSubscribersForLine(String line) {
        String normLine = normalizeLine(line);

        // try DB first
        List<String> db = subscriptionRepository.findByLineCode(normLine)
                .stream()
                .map(WhatsappSubscription::getPhone)
                .toList();
        if (!db.isEmpty()) {
            return db;
        }

        // fallback memory
        Set<String> mem = subscribersByLine.get(normLine);
        if (mem == null || mem.isEmpty()) {
            mem = subscribersByLine.getOrDefault("GENERAL", Set.of());
        }
        return new ArrayList<>(mem);
    }

    public Map<String, Set<String>> getAllSubscriptions() {
        List<WhatsappSubscription> all = subscriptionRepository.findAll();
        if (!all.isEmpty()) {
            Map<String, Set<String>> result = new HashMap<>();
            for (WhatsappSubscription sub : all) {
                result
                        .computeIfAbsent(sub.getLineCode(), k -> new HashSet<>())
                        .add(sub.getPhone());
            }
            return result;
        }
        return subscribersByLine;
    }

    /* =================== BROADCAST =================== */

    public int broadcast(String line, String message) {
        Map<String, Set<String>> all = getAllSubscriptions();

        Set<String> targets;
        if ("GENERAL".equalsIgnoreCase(line)) {
            // flatten: Set<Set<String>> -> Set<String>
            targets = all.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet());
        } else {
            targets = all.getOrDefault(line.toUpperCase(), Set.of());
        }

        int count = 0;
        for (String to : targets) {
            sendWhatsAppMessage(to, message);
            count++;
        }
        return count;
    }

    /* =================== SEND =================== */

    public void sendAlert(String body, List<String> recipients) {
        try {
            // trust-all for PoC
            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAll(), new SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((h, s) -> true);

            for (String recipient : recipients) {
                String toRaw = recipient.startsWith("whatsapp:") ? recipient : "whatsapp:" + recipient;
                String to = encodePlus(toRaw);
                String from = encodePlus(fromNumber);

                String formString = "From=" + from + "&To=" + to + "&Body=" + urlEncode(body);

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
            throw new RuntimeException("Twilio send failed: " + e.getMessage(), e);
        }
    }

    private void sendWhatsAppMessage(String to, String message) {
        sendAlert(message, List.of(to));
    }

    /* =================== AUDIT =================== */

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
        if (auditLog.size() > 200) {
            auditLog.remove(0);
        }
    }

    public List<Map<String, Object>> getAuditLog() {
        return Collections.unmodifiableList(auditLog);
    }

    /* =================== HELPERS =================== */

    private String normalizeLine(String line) {
        return (line == null || line.isBlank()) ? "GENERAL" : line.toUpperCase();
    }

    private String normalizePhone(String phone) {
        String p = phone.trim();
        if (p.startsWith("whatsapp:")) {
            String n = p.substring("whatsapp:".length()).trim();
            if (!n.startsWith("+")) {
                n = "+" + n;
            }
            return "whatsapp:" + n;
        }
        if (p.startsWith("+")) {
            return "whatsapp:" + p;
        }
        return "whatsapp:+" + p;
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

    // "whatsapp:+65..." -> "whatsapp:%2B65..."
    private String encodePlus(String v) {
        return v.replace("+", "%2B");
    }
}
