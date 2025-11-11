package com.lta.whatsapp.controller;

import com.lta.whatsapp.service.WhatsappService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsappController {

    private static final Logger log = LoggerFactory.getLogger(WhatsappController.class);

    private final WhatsappService whatsappService;

    public WhatsappController(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    @PostMapping("/alerts/simple")
    public ResponseEntity<?> sendSimpleAlert(@RequestBody Map<String, Object> payload) {
        try {
            // Extract message
            String message = (String) payload.getOrDefault("message", "");
            if (message.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "message is required"));
            }

            // Detect MRT line from message
            String line = "GENERAL";
            if (message.toUpperCase().contains("NEL")) line = "NEL";
            else if (message.toUpperCase().contains("NSL")) line = "NSL";
            else if (message.toUpperCase().contains("EWL")) line = "EWL";
            else if (message.toUpperCase().contains("CCL")) line = "CCL";
            else if (message.toUpperCase().contains("DTL")) line = "DTL";
            else if (message.toUpperCase().contains("TEL")) line = "TEL";
            else if (message.toUpperCase().contains("BPLRT")) line = "BPLRT";
            else if (message.toUpperCase().contains("SPLRT")) line = "SPLRT";

            //read tets flag
            boolean test = false;
            Object testVal = payload.get("test");
            if (testVal instanceof Boolean b) test = b;


            // ✅ Use your service’s helper method
            List<String> recipients;
            if (test) {
                recipients = List.of("whatsapp:+6584685816");
            }
            else {
                recipients = whatsappService.getRecipientsForLine(line);
            }

            if (recipients == null || recipients.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "No recipients configured for line " + line
                ));
            }

            // Format message
            String formatted = "🚇 " + line + " Service Update\n" + message;

            // Send WhatsApp alert
            whatsappService.sendAlert(formatted, recipients);

            //log to console
            log.info("[{}] SENT {} line={} recipients={} msg={}",
                    OffsetDateTime.now(),
                    (test ? "TEST" : "REAL"),
                    line,
                    recipients,
                    message);

            //add to in-memory audit
            whatsappService.addAuditEntry(
                    OffsetDateTime.now().toString(),
                    line,
                    message,
                    recipients,
                    test
            );

            // Return result
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
}
