package com.dataart.tickets.epic;

import com.dataart.tickets.epic.dto.EpicCreateRequest;
import com.dataart.tickets.epic.dto.EpicResponse;
import com.dataart.tickets.epic.dto.EpicUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Epic CRUD (architecture.md §8, FR-E1..E5,E8). Authenticated. List/create are scoped by the
 * {@code teamId} query parameter (FR-E1/E3).
 */
@RestController
@RequestMapping("/api/epics")
public class EpicController {

    private final EpicService epics;

    public EpicController(EpicService epics) {
        this.epics = epics;
    }

    @GetMapping
    public List<EpicResponse> list(@RequestParam("teamId") UUID teamId) {
        return epics.listByTeam(teamId).stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public EpicResponse get(@PathVariable UUID id) {
        return toResponse(epics.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EpicResponse create(@RequestParam("teamId") UUID teamId,
                               @Valid @RequestBody EpicCreateRequest request) {
        return toResponse(epics.create(teamId, request.title(), request.description()));
    }

    @PutMapping("/{id}")
    public EpicResponse update(@PathVariable UUID id, @Valid @RequestBody EpicUpdateRequest request) {
        return toResponse(epics.update(id, request.title(), request.description(), request.teamId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        epics.delete(id);
        return ResponseEntity.noContent().build();
    }

    private EpicResponse toResponse(Epic epic) {
        return EpicResponse.from(epic, epics.referenceCount("tickets", epic.getId()));
    }
}
