package com.lta.whatsapp.controller;

import com.lta.whatsapp.model.WhatsappAudit;
import com.lta.whatsapp.service.WhatsappService;
import com.lta.whatsapp.repo.WhatsappAuditRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappController.class);

    private final WhatsappService whatsappService;
    private final WhatsappAuditRepository auditRepo;

    public WhatsappController(WhatsappService whatsappService, WhatsappAuditRepository auditRepo) {
        this.whatsappService = whatsappService;
        this.auditRepo = auditRepo;
    }

    // =============== 1) send alert (auto-detect line) ===============
    @PostMapping("/alerts/simple")
    public ResponseEntity<?> sendSimpleAlert(@RequestBody Map<String, Object> payload) {
        try {
            // message
            String message = (String) payload.getOrDefault("message", "");
            if (message.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
            }

            // detect line
            String upper = message.toUpperCase();
            String line = "GENERAL";
            if (upper.contains("NEL")) line = "NEL";
            else if (upper.contains("NSL")) line = "NSL";
            else if (upper.contains("EWL")) line = "EWL";
            else if (upper.contains("CCL")) line = "CCL";
            else if (upper.contains("DTL")) line = "DTL";
            else if (upper.contains("TEL")) line = "TEL";
            else if (upper.contains("BPLRT")) line = "BPLRT";
            else if (upper.contains("SPLRT")) line = "SPLRT";

            // test flag
            boolean test = false;
            Object testVal = payload.get("test");
            if (testVal instanceof Boolean b) {
                test = b;
            }

            // choose recipients
            List<String> recipients;
            if (test) {
                recipients = List.of("whatsapp:+6584685816"); // your own test number
            } else {
                recipients = whatsappService.getSubscribersForLine(line);
            }

            if (recipients == null || recipients.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "No recipients/subscribers configured for line " + line
                ));
            }

            // format
            String formatted = "🚇 " + line + " Service Update\n" + message;

            // send
            whatsappService.sendAlert(formatted, recipients);

            // log
            log.info("[{}] SENT {} line={} recipients={} msg={}",
                    OffsetDateTime.now(),
                    (test ? "TEST" : "REAL"),
                    line,
                    recipients,
                    message);

            // audit
            whatsappService.addAuditEntry(
                    OffsetDateTime.now().toString(),
                    line,
                    message,
                    recipients,
                    test
            );

            return ResponseEntity.ok(Map.of(
                    "status", "sent",
                    "line", line,
                    "test", test,
                    "recipients", recipients
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "failed",
                    "reason", e.getMessage()
            ));
        }
    }

    // =============== 2) subscribe ===============
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(@RequestBody Map<String, Object> payload) {
        String line = (String) payload.getOrDefault("line", "GENERAL");
        String phone = (String) payload.get("phone");

        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone is required"));
        }

        whatsappService.subscribe(line, phone);
        return ResponseEntity.ok(Map.of(
                "status", "subscribed",
                "line", line.toUpperCase(),
                "phone", phone
        ));
    }

    // =============== 3) unsubscribe ===============
    @PostMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, Object> payload) {
        String line = (String) payload.getOrDefault("line", "GENERAL");
        String phone = (String) payload.get("phone");

        if (phone == null || phone.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "phone is required"));
        }

        whatsappService.unsubscribe(line, phone);
        return ResponseEntity.ok(Map.of(
                "status", "unsubscribed",
                "line", line.toUpperCase(),
                "phone", phone
        ));
    }

    // =============== 4) list all subscriptions (for debugging) ===============
    @GetMapping("/subscriptions")
    public ResponseEntity<?> listSubscriptions() {
        return ResponseEntity.ok(whatsappService.getAllSubscriptions());
    }

    // =============== 5) NEW: force a line ===============
    @PostMapping("/alerts/line/{line}")
    public ResponseEntity<?> sendToSpecificLine(
            @PathVariable("line") String line,
            @RequestBody Map<String, Object> payload
    ) {
        try {
            String message = (String) payload.getOrDefault("message", "");
            if (message == null || message.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
            }

            // keep your existing test mechanism
            boolean test = false;
            Object testVal = payload.get("test");
            if (testVal instanceof Boolean b) {
                test = b;
            }

            String lineUpper = line.toUpperCase();

            List<String> recipients;
            if (test) {
                recipients = List.of("whatsapp:+6584685816");
            } else {
                recipients = whatsappService.getSubscribersForLine(lineUpper);
            }

            if (recipients == null || recipients.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "No recipients/subscribers configured for line " + lineUpper
                ));
            }

            String formatted = "🚇 " + lineUpper + " Service Update\n" + message;

            whatsappService.sendAlert(formatted, recipients);

            log.info("[{}] SENT {} line={} recipients={} msg={}",
                    OffsetDateTime.now(),
                    (test ? "TEST" : "REAL"),
                    lineUpper,
                    recipients,
                    message);

            whatsappService.addAuditEntry(
                    OffsetDateTime.now().toString(),
                    lineUpper,
                    message,
                    recipients,
                    test
            );

            return ResponseEntity.ok(Map.of(
                    "status", "sent",
                    "line", lineUpper,
                    "test", test,
                    "recipients", recipients
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "status", "failed",
                    "reason", e.getMessage()
            ));
        }
    }

    // =============== 6) NEW: get audit logs for last 7 days ===============
    @GetMapping("/audit/last7d")
    public ResponseEntity<?> getLast7DaysAudit() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<WhatsappAudit> logs = auditRepo.findByCreatedAtAfterOrderByCreatedAtDesc(sevenDaysAgo);
        return ResponseEntity.ok(logs);
    }

}
