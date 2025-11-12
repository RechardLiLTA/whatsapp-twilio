package com.lta.whatsapp.repo;

import com.lta.whatsapp.model.WhatsappAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface WhatsappAuditRepository extends JpaRepository<WhatsappAudit, Long> {
  List<WhatsappAudit> findByCreatedAtAfterOrderByCreatedAtDesc(LocalDateTime after);
}
