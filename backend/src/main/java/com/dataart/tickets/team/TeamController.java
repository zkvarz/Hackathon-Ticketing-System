package com.dataart.tickets.team;

import com.dataart.tickets.team.dto.TeamRequest;
import com.dataart.tickets.team.dto.TeamResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Team CRUD (architecture.md §8, FR-T1..T6). Authenticated; all verified users manage all teams
 * (FR-T6). Validation/conflict handling is centralized in the exception handler.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamController {

    private final TeamService teams;

    public TeamController(TeamService teams) {
        this.teams = teams;
    }

    @GetMapping
    public List<TeamResponse> list() {
        return teams.list().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    public TeamResponse get(@PathVariable UUID id) {
        return toResponse(teams.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(@Valid @RequestBody TeamRequest request) {
        return toResponse(teams.create(request.name()));
    }

    @PutMapping("/{id}")
    public TeamResponse rename(@PathVariable UUID id, @Valid @RequestBody TeamRequest request) {
        return toResponse(teams.rename(id, request.name()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        teams.delete(id);
        return ResponseEntity.noContent().build();
    }

    private TeamResponse toResponse(Team team) {
        return TeamResponse.from(team,
                teams.referenceCount("epics", team.getId()),
                teams.referenceCount("tickets", team.getId()));
    }
}
