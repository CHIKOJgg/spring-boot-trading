package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.*;
import org.example.dto.request.UserRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.exception.GlobalExceptionHandler.*;
import org.example.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    public UserResponse getByUsername(String username) {
        UserEntity u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        return toUserResponse(u);
    }

    public UserResponse getById(Long id) {
        return toUserResponse(findUser(id));
    }

    public Page<UserResponse> getAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toUserResponse);
    }

    public ProfileResponse getProfile(String username) {
        UserEntity u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
        ClientProfileEntity p = u.getProfile();
        if (p == null) return null;
        return toProfileResponse(p);
    }

    @Transactional
    public ProfileResponse updateProfile(String username, UpdateProfileRequest request) {
        UserEntity u = userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));

        ClientProfileEntity p = u.getProfile();
        if (p == null) {
            p = ClientProfileEntity.builder().user(u).createdAt(LocalDateTime.now()).build();
        }
        if (request.firstName()    != null) p.setFirstName(request.firstName());
        if (request.lastName()     != null) p.setLastName(request.lastName());
        if (request.phone()        != null) p.setPhone(request.phone());
        if (request.address()      != null) p.setAddress(request.address());
        if (request.dateOfBirth()  != null) p.setDateOfBirth(request.dateOfBirth());
        u.setProfile(p);
        userRepository.save(u);
        auditService.logSuccess(username, "UPDATE_PROFILE", "USER", u.getId().toString());
        return toProfileResponse(p);
    }

    // -------------------------------------------------------
    //  Admin operations
    // -------------------------------------------------------

    @Transactional
    public UserResponse adminUpdate(Long userId, AdminUpdateUserRequest request, String adminUsername) {
        UserEntity u = findUser(userId);
        if (request.isActive() != null)  u.setIsActive(request.isActive());
        if (request.isLocked() != null)  u.setIsLocked(request.isLocked());
        if (request.role()     != null) {
            RoleEntity role = roleRepository.findByName(request.role())
                    .orElseThrow(() -> new TradingException("Role not found: " + request.role()));
            u.getRoles().clear();
            u.getRoles().add(role);
        }
        userRepository.save(u);
        auditService.logSuccess(adminUsername, "ADMIN_UPDATE_USER", "USER", userId.toString());
        return toUserResponse(u);
    }

    @Transactional
    public void lockUser(Long userId, String adminUsername) {
        UserEntity u = findUser(userId);
        u.setIsLocked(true);
        userRepository.save(u);
        auditService.logSuccess(adminUsername, "LOCK_USER", "USER", userId.toString());
    }

    @Transactional
    public void unlockUser(Long userId, String adminUsername) {
        UserEntity u = findUser(userId);
        u.setIsLocked(false);
        userRepository.save(u);
        auditService.logSuccess(adminUsername, "UNLOCK_USER", "USER", userId.toString());
    }

    // -------------------------------------------------------

    private UserEntity findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + id));
    }

    private UserResponse toUserResponse(UserEntity u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                u.getIsActive(), u.getIsLocked(),
                u.getRoles().stream().map(RoleEntity::getName).toList(),
                u.getCreatedAt(), u.getLastLoginAt());
    }

    private ProfileResponse toProfileResponse(ClientProfileEntity p) {
        return new ProfileResponse(p.getId(), p.getUser().getId(),
                p.getFirstName(), p.getLastName(), p.getPhone(),
                p.getAddress(), p.getDateOfBirth(), p.getKycStatus());
    }
}
