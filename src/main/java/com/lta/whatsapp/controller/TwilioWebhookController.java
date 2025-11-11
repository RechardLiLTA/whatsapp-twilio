package com.lta.whatsapp.controller;

import com.lta.whatsapp.service.WhatsappService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/twilio")
public class TwilioWebhookController {

    private final WhatsappService whatsappService;

    public TwilioWebhookController(WhatsappService whatsappService) {
        this.whatsappService = whatsappService;
    }

    @PostMapping(
            value = "/whatsapp",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_XML_VALUE
    )
    public ResponseEntity<String> receiveWhatsApp(
            @RequestParam(value = "From", required = false) String from,
            @RequestParam(value = "Body", required = false) String body
    ) {
        if (from == null || body == null) {
            return twiml("Missing From/Body.");
        }

        // 1) normalise sender
        String sender = from.trim();
        if (sender.startsWith("whatsapp:")) {
            String numberPart = sender.substring("whatsapp:".length()).trim(); // remove spaces
            if (!numberPart.startsWith("+")) {
                numberPart = "+" + numberPart;
            }
            sender = "whatsapp:" + numberPart;
        }

        // make it effectively final for lambdas below
        final String senderFinal = sender;

        String text = body.trim();
        String upper = text.toUpperCase();

        // SUB ...
        if (upper.startsWith("SUB")) {
            List<String> parts = Arrays.stream(upper.split("\\s+")).toList();
            if (parts.size() < 2) {
                return twiml("Tell me which line. Example: SUB NEL");
            }
            List<String> lines = parts.subList(1, parts.size());
            for (String line : lines) {
                whatsappService.subscribe(line, senderFinal);
            }
            return twiml("✅ Subscribed you to: " + String.join(", ", lines));
        }

        // UNSUB ...
        if (upper.startsWith("UNSUB")) {
            List<String> parts = Arrays.stream(upper.split("\\s+")).toList();
            if (parts.size() < 2) {
                return twiml("Tell me which line. Example: UNSUB NEL");
            }
            List<String> lines = parts.subList(1, parts.size());
            for (String line : lines) {
                whatsappService.unsubscribe(line, senderFinal);
            }
            return twiml("✅ Unsubscribed you from: " + String.join(", ", lines));
        }

        // LINES
        if (upper.startsWith("LINES")) {
            Map<String, Set<String>> all = whatsappService.getAllSubscriptions();
            List<String> myLines = all.entrySet().stream()
                    .filter(e -> e.getValue().contains(senderFinal))
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.toList());

            if (myLines.isEmpty()) {
                return twiml("You are not subscribed to any lines. Try: SUB NEL");
            } else {
                return twiml("You are subscribed to: " + String.join(", ", myLines));
            }
        }

        // fallback help
        return twiml("""
                LTA Rail Alerts 👋
                Commands:
                SUB NEL
                SUB EWL
                SUB CCL
                LINES
                UNSUB NEL
                """);
    }

    private ResponseEntity<String> twiml(String message) {
        String xml = """
                <Response>
                  <Message>%s</Message>
                </Response>
                """.formatted(message);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }
}
