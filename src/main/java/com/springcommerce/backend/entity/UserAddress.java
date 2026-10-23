package com.springcommerce.backend.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_address")
public class UserAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    @Column(nullable = false, length = 50)
    private String label;

    @Column(
            name = "recipient_name",
            nullable = false,
            length = 200
    )
    private String recipientName;

    @Column(
            name = "phone_number",
            nullable = false,
            length = 30
    )
    private String phoneNumber;

    @Column(
            name = "postal_code",
            nullable = false,
            length = 20
    )
    private String postalCode;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(
            name = "address_detail",
            length = 500
    )
    private String addressDetail;

    @Column(
            name = "is_default",
            nullable = false
    )
    private boolean defaultAddress;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        LocalDateTime now =
                LocalDateTime.now();

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt =
                LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(
            String recipientName
    ) {
        this.recipientName =
                recipientName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(
            String phoneNumber
    ) {
        this.phoneNumber =
                phoneNumber;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(
            String postalCode
    ) {
        this.postalCode =
                postalCode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(
            String address
    ) {
        this.address = address;
    }

    public String getAddressDetail() {
        return addressDetail;
    }

    public void setAddressDetail(
            String addressDetail
    ) {
        this.addressDetail =
                addressDetail;
    }

    public boolean isDefaultAddress() {
        return defaultAddress;
    }

    public void setDefaultAddress(
            boolean defaultAddress
    ) {
        this.defaultAddress =
                defaultAddress;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

}
