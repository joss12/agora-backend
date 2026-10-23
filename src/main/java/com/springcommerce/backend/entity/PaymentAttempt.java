
package com.springcommerce.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_attempt")
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentOutcome outcome;

    @Column(name = "provider", length = 20)
    private String provider;

    @Column(name = "provider_status", length = 30)
    private String providerStatus;

    @Column(
            name = "transaction_reference",
            nullable = false,
            unique = true,
            length = 100
    )
    private String transactionReference;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public PaymentOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(PaymentOutcome outcome) {
        this.outcome = outcome;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public void setTransactionReference(
            String transactionReference
    ) {
        this.transactionReference = transactionReference;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public void setProviderStatus(String providerStatus) {
        this.providerStatus = providerStatus;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
