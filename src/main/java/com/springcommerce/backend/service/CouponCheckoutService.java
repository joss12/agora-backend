package com.springcommerce.backend.service;

import com.springcommerce.backend.entity.*;
import com.springcommerce.backend.repository.CouponRedemptionRepository;
import com.springcommerce.backend.repository.CouponRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class CouponCheckoutService {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;

    public CouponCheckoutService(
            CouponRepository couponRepository,
            CouponRedemptionRepository redemptionRepository
    ) {
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal apply(
            String code,
            BigDecimal subtotal,
            User user,
            Order order
    ) {
        if (code == null || code.isBlank()) {
            return BigDecimal.ZERO;
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);

        Coupon coupon = couponRepository
                .findByCodeForUpdate(normalized)
                .orElseThrow(() -> invalid("Invalid coupon code"));

        LocalDateTime now = LocalDateTime.now();

        if (!coupon.isActive()
                || now.isBefore(coupon.getStartsAt())
                || !now.isBefore(coupon.getExpiresAt())) {
            throw invalid("Coupon is inactive or expired");
        }

        if (subtotal.compareTo(coupon.getMinimumOrderAmount()) < 0) {
            throw invalid("Minimum order amount not reached");
        }

        if (coupon.getUsageLimit() != null
                && coupon.getUsageCount() >= coupon.getUsageLimit()) {
            throw invalid("Coupon usage limit reached");
        }

        long userUsage = redemptionRepository
                .countByCouponIdAndUserId(
                        coupon.getId(),
                        user.getId()
                );

        if (coupon.getUsageLimitPerUser() != null
                && userUsage >= coupon.getUsageLimitPerUser()) {
            throw invalid("You have already reached the coupon usage limit");
        }

        BigDecimal discount;

        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = subtotal
                    .multiply(coupon.getDiscountValue())
                    .divide(
                            new BigDecimal("100"),
                            2,
                            RoundingMode.HALF_UP
                    );

            if (coupon.getMaximumDiscountAmount() != null) {
                discount = discount.min(
                        coupon.getMaximumDiscountAmount()
                );
            }
        } else {
            discount = coupon.getDiscountValue();
        }

        discount = discount
                .min(subtotal)
                .setScale(2, RoundingMode.HALF_UP);

        if (discount.signum() <= 0) {
            throw invalid("Coupon discount must be greater than zero");
        }

        CouponRedemption redemption = new CouponRedemption();
        redemption.setCoupon(coupon);
        redemption.setUser(user);
        redemption.setOrder(order);
        redemption.setDiscountAmount(discount);

        redemptionRepository.save(redemption);

        coupon.setUsageCount(coupon.getUsageCount() + 1);
        couponRepository.save(coupon);

        return discount;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void release(Long orderId) {
        CouponRedemption redemption = redemptionRepository
                .findByOrderId(orderId)
                .orElse(null);

        if (redemption == null) {
            return;
        }

        Coupon coupon = couponRepository
                .findByIdForUpdate(redemption.getCoupon().getId())
                .orElseThrow(() ->
                        new IllegalStateException("Coupon not found")
                );

        redemptionRepository.delete(redemption);
        redemptionRepository.flush();

        coupon.setUsageCount(
                Math.max(0, coupon.getUsageCount() - 1)
        );

        couponRepository.save(coupon);
    }

    private ResponseStatusException invalid(String message) {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                message
        );
    }
}
