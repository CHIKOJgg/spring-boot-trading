package org.example.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.dto.request.UserRequest.*;
import org.example.dto.response.ApiResponse.*;
import org.example.service.NotificationService;
import org.example.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(userService.getByUsername(user.getUsername()));
    }

    @GetMapping("/me/profile")
    public ResponseEntity<ProfileResponse> getProfile(@AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok(userService.getProfile(user.getUsername()));
    }

    @PutMapping("/me/profile")
    public ResponseEntity<ProfileResponse> updateProfile(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(user.getUsername(), request));
    }

    @GetMapping("/me/notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserDetails user) {
        UserResponse u = userService.getByUsername(user.getUsername());
        return ResponseEntity.ok(notificationService.getForUser(u.id()));
    }

    @PostMapping("/me/notifications/read")
    public ResponseEntity<SuccessResponse> markNotificationsRead(
            @AuthenticationPrincipal UserDetails user) {
        UserResponse u = userService.getByUsername(user.getUsername());
        notificationService.markAllRead(u.id());
        return ResponseEntity.ok(SuccessResponse.of("Notifications marked as read"));
    }

    // -------------------------------------------------------
    //  Admin endpoints
    // -------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<UserResponse>> listUsers(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.getAll(pageable));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> adminUpdate(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequest request,
            @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(userService.adminUpdate(id, request, admin.getUsername()));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse> lock(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails admin) {
        userService.lockUser(id, admin.getUsername());
        return ResponseEntity.ok(SuccessResponse.of("User locked"));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SuccessResponse> unlock(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails admin) {
        userService.unlockUser(id, admin.getUsername());
        return ResponseEntity.ok(SuccessResponse.of("User unlocked"));
    }
}
