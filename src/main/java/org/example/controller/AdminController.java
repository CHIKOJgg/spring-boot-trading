package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.InstrumentEntity;
import org.example.dto.request.UserRequest.AdminUpdateUserRequest;
import org.example.dto.response.ApiResponse.*;
import org.example.repository.InstrumentRepository;
import org.example.service.AuditService;
import org.example.service.OrderService;
import org.example.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AuditService    auditService;
    private final UserService     userService;
    private final OrderService    orderService;
    private final InstrumentRepository instrumentRepository;

    // -------------------------------------------------------
    //  Audit log
    // -------------------------------------------------------

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @PageableDefault(size = 50, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditService.getAll(pageable));
    }

    @GetMapping("/audit/user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(auditService.getByUser(userId, pageable));
    }

    // -------------------------------------------------------
    //  User management
    // -------------------------------------------------------

    @GetMapping("/users")
    public ResponseEntity<Page<UserResponse>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(userService.getAll(pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    /** General update: role, isActive, isLocked in one call */
    @PutMapping("/users/{id}")
    public ResponseEntity<UserResponse> updateUser(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequest request,
            @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(userService.adminUpdate(id, request, admin.getUsername()));
    }

    /** Lock (ban) a user — they can no longer log in */
    @PutMapping("/users/{id}/lock")
    public ResponseEntity<UserResponse> lockUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(userService.lockUser(id, admin.getUsername()));
    }

    /** Unlock (unban) a user */
    @PutMapping("/users/{id}/unlock")
    public ResponseEntity<UserResponse> unlockUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(userService.unlockUser(id, admin.getUsername()));
    }

    /** Deactivate a user account */
    @PutMapping("/users/{id}/deactivate")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(userService.setActive(id, false, admin.getUsername()));
    }

    /** Reactivate a user account */
    @PutMapping("/users/{id}/activate")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails admin) {
        return ResponseEntity.ok(userService.setActive(id, true, admin.getUsername()));
    }

    // -------------------------------------------------------
    //  Order management
    // -------------------------------------------------------

    @GetMapping("/orders")
    public ResponseEntity<Page<OrderResponse>> getAllOrders(
            @PageableDefault(size = 50, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(orderService.getAllOrders(pageable));
    }

    // -------------------------------------------------------
    //  Instrument management
    // -------------------------------------------------------

    @GetMapping("/instruments")
    public ResponseEntity<Page<InstrumentResponse>> getInstruments(
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(instrumentRepository.findAll(pageable)
                .map(this::toInstrumentResponse));
    }

    @PostMapping("/instruments")
    public ResponseEntity<InstrumentResponse> createInstrument(
            @RequestBody InstrumentEntity instrument,
            @AuthenticationPrincipal UserDetails admin) {
        instrument.setCreatedAt(LocalDateTime.now());
        instrument.setIsActive(true);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(toInstrumentResponse(instrumentRepository.save(instrument)));
    }

    @PutMapping("/instruments/{id}/toggle")
    public ResponseEntity<InstrumentResponse> toggleInstrument(@PathVariable Long id) {
        return instrumentRepository.findById(id).map(i -> {
            i.setIsActive(!i.getIsActive());
            return ResponseEntity.ok(toInstrumentResponse(instrumentRepository.save(i)));
        }).orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------

    private InstrumentResponse toInstrumentResponse(InstrumentEntity i) {
        return new InstrumentResponse(i.getId(), i.getTicker(), i.getName(),
                i.getInstrumentType(), i.getCurrency(), i.getLotSize(),
                i.getTickSize(), i.getIsActive());
    }
}
