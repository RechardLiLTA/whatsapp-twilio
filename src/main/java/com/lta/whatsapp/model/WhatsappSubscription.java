package com.lta.whatsapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "whatsapp_subscriptions",
        uniqueConstraints = @UniqueConstraint(columnNames = {"line_code", "phone"})
)
public class WhatsappSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "line_code", nullable = false)
    private String lineCode;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public WhatsappSubscription() {
    }

    public WhatsappSubscription(String lineCode, String phone) {
        this.lineCode = lineCode;
        this.phone = phone;
        this.createdAt = LocalDateTime.now();
    }

    // getters & setters
    public Long getId() {
        return id;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
