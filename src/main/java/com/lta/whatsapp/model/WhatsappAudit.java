package com.lta.whatsapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "whatsapp_audit")
public class WhatsappAudit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String lineCode;

  @Column(nullable = false, length = 1024)
  private String message;

  @Column(nullable = false)
  private int recipientCount;

  @Column(nullable = false)
  private boolean testMode;

  @Column(nullable = false)
  private LocalDateTime createdAt = LocalDateTime.now();

  public WhatsappAudit() {}

  public WhatsappAudit(String lineCode, String message, int recipientCount, boolean testMode) {
    this.lineCode = lineCode;
    this.message = message;
    this.recipientCount = recipientCount;
    this.testMode = testMode;
    this.createdAt = LocalDateTime.now();
  }

  // getters (add setters only if you need them)
  public Long getId() { return id; }
  public String getLineCode() { return lineCode; }
  public String getMessage() { return message; }
  public int getRecipientCount() { return recipientCount; }
  public boolean isTestMode() { return testMode; }
  public LocalDateTime getCreatedAt() { return createdAt; }
}
