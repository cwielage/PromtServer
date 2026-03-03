package com.lafayette.promptserver.controller;

import com.lafayette.promptserver.dto.InvitationCodeDto;
import com.lafayette.promptserver.model.InvitationCode;
import com.lafayette.promptserver.repository.InvitationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationCodeRepository invitationCodeRepository;

    @GetMapping
    public ResponseEntity<List<InvitationCodeDto>> listInvitations() {
        List<InvitationCodeDto> list = invitationCodeRepository.findAll()
                .stream()
                .sorted(Comparator.comparing(InvitationCode::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toDto)
                .toList();
        return ResponseEntity.ok(list);
    }

    @PostMapping
    public ResponseEntity<InvitationCodeDto> generateInvitation(Authentication authentication) {
        String code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        InvitationCode invite = InvitationCode.builder()
                .code(code)
                .createdBy(authentication.getName())
                .used(false)
                .build();
        invitationCodeRepository.save(invite);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(invite));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revokeInvitation(@PathVariable String id) {
        if (!invitationCodeRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invitation not found");
        }
        invitationCodeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private InvitationCodeDto toDto(InvitationCode ic) {
        return new InvitationCodeDto(
                ic.getId(), ic.getCode(), ic.getCreatedBy(),
                ic.isUsed(), ic.getUsedBy(), ic.getCreatedAt(), ic.getUsedAt()
        );
    }
}
