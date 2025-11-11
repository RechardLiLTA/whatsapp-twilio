package com.lta.whatsapp.repo;

import com.lta.whatsapp.model.WhatsappSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WhatsappSubscriptionRepository extends JpaRepository<WhatsappSubscription, Long> {
    List<WhatsappSubscription> findByLineCode(String lineCode);
    Optional<WhatsappSubscription> findByLineCodeAndPhone(String lineCode, String phone);
    void deleteByLineCodeAndPhone(String lineCode, String phone);
}
