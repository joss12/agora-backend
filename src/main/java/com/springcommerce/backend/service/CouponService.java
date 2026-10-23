package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.CouponRequest;
import com.springcommerce.backend.dto.CouponResponse;
import com.springcommerce.backend.entity.Coupon;
import com.springcommerce.backend.entity.DiscountType;
import com.springcommerce.backend.repository.CouponRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class CouponService {

    private final CouponRepository couponRepository;

    public CouponService(CouponRepository couponRepository) {
        this.couponRepository = couponRepository;
    }

    @Transactional
    public CouponResponse create(CouponRequest request) {

        String code = normalizeCode(request.code());

        validateRequest(request);

        if (couponRepository.existsByCodeIgnoreCase(code)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Coupon code already exists"
            );
        }

        Coupon coupon = new Coupon();

        applyRequest(coupon, request, code);

        try {
            return toResponse(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Coupon could not be created. Code may already exist.",
                    exception
            );
        }
    }

    @Transactional(readOnly = true)
    public List<CouponResponse> findAll() {

        return couponRepository
                .findByDeletedFalseOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CouponResponse findById(Long id) {
        return toResponse(getCoupon(id));
    }

    @Transactional
    public CouponResponse update(Long id, CouponRequest request) {

        Coupon coupon = getCoupon(id);

        String code = normalizeCode(request.code());

        validateRequest(request);

        if (!coupon.getCode().equalsIgnoreCase(code)
                && couponRepository.existsByCodeIgnoreCase(code)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Coupon code already exists"
            );
        }

        applyRequest(coupon, request, code);

        try {
            return toResponse(couponRepository.saveAndFlush(coupon));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Coupon could not be updated. Check code uniqueness.",
                    exception
            );
        }
    }

    @Transactional
    public CouponResponse updateStatus(Long id, boolean active) {

        Coupon coupon = getCoupon(id);

        coupon.setActive(active);

        return toResponse(couponRepository.save(coupon));
    }

    @Transactional
    public void delete(Long id) {

        Coupon coupon = getCoupon(id);

        coupon.setActive(false);
        coupon.setDeleted(true);

        couponRepository.save(coupon);
    }

    private Coupon getCoupon(Long id) {

        return couponRepository
                .findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Coupon not found"
                ));
    }

    private void validateRequest(CouponRequest request) {

        if (request.expiresAt().isBefore(request.startsAt())
                || request.expiresAt().isEqual(request.startsAt())) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expiration date must be after start date"
            );
        }

        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(
                new BigDecimal("100")
        ) > 0) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Percentage discount cannot exceed 100"
            );
        }

        if (request.discountType() == DiscountType.FIXED
                && request.maximumDiscountAmount() != null) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Maximum discount amount is only valid for percentage coupons"
            );
        }

        if (request.usageLimit() != null
                && request.usageLimitPerUser() != null
                && request.usageLimitPerUser() > request.usageLimit()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Per-user usage limit cannot exceed total usage limit"
            );
        }
    }

    private String normalizeCode(String code) {

        return code.trim().toUpperCase(Locale.ROOT);
    }

    private void applyRequest(
            Coupon coupon,
            CouponRequest request,
            String code
    ) {

        coupon.setCode(code);
        coupon.setDiscountType(request.discountType());
        coupon.setDiscountValue(request.discountValue());

        coupon.setMinimumOrderAmount(
                request.minimumOrderAmount() == null
                        ? BigDecimal.ZERO
                        : request.minimumOrderAmount()
        );

        coupon.setMaximumDiscountAmount(
                request.maximumDiscountAmount()
        );

        coupon.setUsageLimit(request.usageLimit());
        coupon.setUsageLimitPerUser(request.usageLimitPerUser());

        coupon.setStartsAt(request.startsAt());
        coupon.setExpiresAt(request.expiresAt());

        coupon.setActive(request.active());
    }

    private CouponResponse toResponse(Coupon coupon) {

        return new CouponResponse(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountType(),
                coupon.getDiscountValue(),
                coupon.getMinimumOrderAmount(),
                coupon.getMaximumDiscountAmount(),
                coupon.getUsageLimit(),
                coupon.getUsageLimitPerUser(),
                coupon.getUsageCount(),
                coupon.getStartsAt(),
                coupon.getExpiresAt(),
                coupon.isActive(),
                coupon.getCreatedAt(),
                coupon.getUpdatedAt()
        );
    }
}