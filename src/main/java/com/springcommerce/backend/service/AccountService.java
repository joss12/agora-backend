package com.springcommerce.backend.service;

import com.springcommerce.backend.dto.UserResponse;
import com.springcommerce.backend.dto.account.AddressRequest;
import com.springcommerce.backend.dto.account.AddressResponse;
import com.springcommerce.backend.dto.account.UpdateProfileRequest;
import com.springcommerce.backend.entity.User;
import com.springcommerce.backend.entity.UserAddress;
import com.springcommerce.backend.exception.DuplicateEmailException;
import com.springcommerce.backend.repository.UserAddressRepository;
import com.springcommerce.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final UserAddressRepository addressRepository;

    public AccountService(
            UserRepository userRepository,
            UserAddressRepository addressRepository
    ) {
        this.userRepository =
                userRepository;

        this.addressRepository =
                addressRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(
            String email
    ) {
        return mapUser(
                getUser(email)
        );
    }

    @Transactional
    public UserResponse updateProfile(
            String currentEmail,
            UpdateProfileRequest request
    ) {
        User user =
                getUser(currentEmail);

        String email = request
                .email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (
                !email.equals(user.getEmail()) &&
                        userRepository.existsByEmail(email)
        ) {
            throw new DuplicateEmailException(
                    email
            );
        }

        user.setFirstName(
                request.firstName().trim()
        );

        user.setLastName(
                request.lastName().trim()
        );

        user.setEmail(email);

        User saved =
                userRepository.save(user);

        return mapUser(saved);
    }

    @Transactional(readOnly = true)
    public List<AddressResponse> getAddresses(
            String email
    ) {
        User user =
                getUser(email);

        return addressRepository
                .findAllByUserIdOrderByDefaultAddressDescCreatedAtDesc(
                        user.getId()
                )
                .stream()
                .map(this::mapAddress)
                .toList();
    }

    @Transactional
    public AddressResponse createAddress(
            String email,
            AddressRequest request
    ) {
        User user =
                getUser(email);

        boolean hasAddresses =
                addressRepository.existsByUserId(
                        user.getId()
                );

        boolean makeDefault =
                request.defaultAddress() ||
                        !hasAddresses;

        if (makeDefault) {
            addressRepository
                    .clearDefaultAddress(
                            user.getId()
                    );
        }

        UserAddress address =
                new UserAddress();

        address.setUser(user);

        applyAddress(
                address,
                request
        );

        address.setDefaultAddress(
                makeDefault
        );

        return mapAddress(
                addressRepository.save(
                        address
                )
        );
    }

    @Transactional
    public AddressResponse updateAddress(
            String email,
            Long addressId,
            AddressRequest request
    ) {
        User user =
                getUser(email);

        UserAddress address =
                getAddress(
                        addressId,
                        user.getId()
                );

        if (request.defaultAddress()) {
            addressRepository
                    .clearDefaultAddress(
                            user.getId()
                    );
        }

        applyAddress(
                address,
                request
        );

        if (request.defaultAddress()) {
            address.setDefaultAddress(true);
        }

        return mapAddress(
                addressRepository.save(
                        address
                )
        );
    }

    @Transactional
    public AddressResponse setDefaultAddress(
            String email,
            Long addressId
    ) {
        User user =
                getUser(email);

        UserAddress address =
                getAddress(
                        addressId,
                        user.getId()
                );

        addressRepository
                .clearDefaultAddress(
                        user.getId()
                );

        address.setDefaultAddress(true);

        return mapAddress(
                addressRepository.save(
                        address
                )
        );
    }

    @Transactional
    public void deleteAddress(
            String email,
            Long addressId
    ) {
        User user =
                getUser(email);

        UserAddress address =
                getAddress(
                        addressId,
                        user.getId()
                );

        boolean wasDefault =
                address.isDefaultAddress();

        addressRepository.delete(address);
        addressRepository.flush();

        if (!wasDefault) {
            return;
        }

        List<UserAddress> remaining =
                addressRepository
                        .findAllByUserIdOrderByDefaultAddressDescCreatedAtDesc(
                                user.getId()
                        );

        if (!remaining.isEmpty()) {
            UserAddress replacement =
                    remaining.get(0);

            replacement.setDefaultAddress(
                    true
            );

            addressRepository.save(
                    replacement
            );
        }
    }

    private User getUser(
            String email
    ) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED
                                        )
                        );

        if (!user.isEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED
            );
        }

        return user;
    }

    private UserAddress getAddress(
            Long addressId,
            Long userId
    ) {
        return addressRepository
                .findByIdAndUserId(
                        addressId,
                        userId
                )
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Address not found"
                                )
                );
    }

    private void applyAddress(
            UserAddress address,
            AddressRequest request
    ) {
        address.setLabel(
                request.label().trim()
        );

        address.setRecipientName(
                request.recipientName().trim()
        );

        address.setPhoneNumber(
                request.phoneNumber().trim()
        );

        address.setPostalCode(
                request.postalCode().trim()
        );

        address.setAddress(
                request.address().trim()
        );

        String detail =
                request.addressDetail();

        address.setAddressDetail(
                detail == null ||
                        detail.isBlank()
                        ? null
                        : detail.trim()
        );
    }

    private UserResponse mapUser(
            User user
    ) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getRole().name(),
                user.isEnabled(),
                user.getCreatedAt()
        );
    }

    private AddressResponse mapAddress(
            UserAddress address
    ) {
        return new AddressResponse(
                address.getId(),
                address.getLabel(),
                address.getRecipientName(),
                address.getPhoneNumber(),
                address.getPostalCode(),
                address.getAddress(),
                address.getAddressDetail(),
                address.isDefaultAddress(),
                address.getCreatedAt(),
                address.getUpdatedAt()
        );
    }
}