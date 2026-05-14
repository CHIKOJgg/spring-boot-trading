package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.domain.model.entity.InstrumentEntity;
import org.example.dto.response.ApiResponse.*;
import org.example.repository.InstrumentRepository;
import org.example.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    private final AuditService auditService;
    private final InstrumentRepository instrumentRepository;

    @GetMapping("/audit")
    public ResponseEntity<Page<AuditLogResponse>> getAuditLogs(
            @PageableDefault(size = 50, sort = "createdAt",
                    direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(auditService.getAll(pageable));
    }

    @GetMapping("/audit/user/{userId}")
    public ResponseEntity<Page<AuditLogResponse>> getAuditByUser(
            @PathVariable Long userId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(auditService.getByUser(userId, pageable));
    }

    // -------------------------------------------------------
    //  Instrument management
    // -------------------------------------------------------

    @PostMapping("/instruments")
    public ResponseEntity<InstrumentResponse> createInstrument(
            @RequestBody InstrumentEntity instrument,
            @AuthenticationPrincipal UserDetails admin) {
        instrument.setCreatedAt(LocalDateTime.now());
        InstrumentEntity saved = instrumentRepository.save(instrument);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(saved));
    }

    @PutMapping("/instruments/{id}/toggle")
    public ResponseEntity<InstrumentResponse> toggleInstrument(@PathVariable Long id) {
        return instrumentRepository.findById(id).map(i -> {
            i.setIsActive(!i.getIsActive());
            return ResponseEntity.ok(toResponse(instrumentRepository.save(i)));
        }).orElse(ResponseEntity.notFound().build());
    }

    private InstrumentResponse toResponse(InstrumentEntity i) {
        return new InstrumentResponse(i.getId(), i.getTicker(), i.getName(),
                i.getInstrumentType(), i.getCurrency(), i.getLotSize(), i.getTickSize(), i.getIsActive());
    }
}
