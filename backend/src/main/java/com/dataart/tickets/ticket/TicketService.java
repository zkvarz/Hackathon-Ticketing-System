package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.EmailNormalizer;
import com.dataart.tickets.auth.User;
import com.dataart.tickets.auth.UserRepository;
import com.dataart.tickets.common.NotFoundException;
import com.dataart.tickets.epic.Epic;
import com.dataart.tickets.epic.EpicRepository;
import com.dataart.tickets.team.Team;
import com.dataart.tickets.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ticket business logic (HTS-019, FR-K1..K4,K6,K8). All references (team, optional epic) and
 * enums are validated server-side; {@code createdBy} is taken from the authenticated principal,
 * never the client. Updates diff incoming vs stored so {@code modifiedAt} advances only on a real
 * change (AMB-3); deletion cascades comments at the DB level (FR-K6).
 *
 * <p>When an epic is set, it must belong to the ticket's team (FR-E7); a team change that would
 * leave the current epic cross-team is rejected unless the request also clears or replaces the
 * epic (FR-K5, HTS-021). A null epic is always allowed.
 */
@Service
public class TicketService {

    private final TicketRepository tickets;
    private final TeamRepository teams;
    private final EpicRepository epics;
    private final UserRepository users;
    private final TicketActivityRepository activity;
    private final Clock clock;

    public TicketService(TicketRepository tickets, TeamRepository teams, EpicRepository epics,
                         UserRepository users, TicketActivityRepository activity, Clock clock) {
        this.tickets = tickets;
        this.teams = teams;
        this.epics = epics;
        this.users = users;
        this.activity = activity;
        this.clock = clock;
    }

    /**
     * Board query for a team with optional filters (HTS-025 + HTS-029). All filters are optional
     * and AND-combined; a blank {@code q} is treated as absent (not match-nothing). No filters =
     * the full board, most-recently-modified first.
     */
    @Transactional(readOnly = true)
    public List<Ticket> search(UUID teamId, TicketType type, UUID epicId, String q) {
        String query = (q == null || q.isBlank()) ? null : q.trim();
        return tickets.search(teamId, type, epicId, query);
    }

    @Transactional(readOnly = true)
    public Ticket get(UUID id) {
        return tickets.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found: " + id));
    }

    @Transactional
    public Ticket create(UUID teamId, UUID epicId, TicketType type, TicketState state,
                         String title, String body, String creatorEmail) {
        Team team = requireTeam(teamId);
        Epic epic = resolveEpic(epicId);
        requireEpicSameTeam(team, epic);
        User creator = users.findByEmail(EmailNormalizer.normalize(creatorEmail))
                .orElseThrow(() -> new NotFoundException("User not found: " + creatorEmail));
        TicketState initialState = state == null ? TicketState.NEW : state;
        Ticket ticket = new Ticket(team, epic, type, initialState, title.trim(), body.trim(), creator);
        tickets.save(ticket);
        // Record the creation as a single append-only activity entry (HTS-041, AC-1).
        activity.save(new TicketActivity(ticket, creator.getEmail(), TicketFieldChange.CREATED,
                null, null, clock.instant()));
        return ticket;
    }

    @Transactional
    public Ticket update(UUID id, UUID teamId, UUID epicId, TicketType type, TicketState state,
                         String title, String body, String actorEmail) {
        Ticket ticket = get(id);
        Team team = requireTeam(teamId);
        Epic epic = resolveEpic(epicId);
        requireEpicSameTeam(team, epic);
        // Field-level diffing: modified_at advances only if something actually changed (AMB-3).
        // The timestamp itself is set by JPA Auditing when the change dirties the row (HTS-047).
        List<TicketFieldChange> changes = ticket.applyChangesTracked(team, epic, type, state,
                title.trim(), body.trim());
        // One append-only activity row per changed field; a no-op edit records nothing (AC-3).
        recordActivity(ticket, changes, actorEmail);
        return ticket;
    }

    /**
     * Change only the workflow state (HTS-027, board drag-drop). Persists immediately and always
     * advances {@code modified_at} so the board re-sorts (FR-K7, FR-B6). 404 if the ticket is gone.
     */
    @Transactional
    public Ticket changeState(UUID id, TicketState newState, String actorEmail) {
        Ticket ticket = get(id);
        TicketState oldState = ticket.getState();
        Instant now = clock.instant();
        ticket.changeState(newState, now);
        // Record only a real transition (AC-3): re-setting the same state still bumps modified_at
        // (the HTS-027 board exception) but is not a history-worthy change.
        if (oldState != newState) {
            activity.save(new TicketActivity(ticket, EmailNormalizer.normalize(actorEmail),
                    TicketFieldChange.STATE, oldState.wire(), newState.wire(), now));
        }
        return ticket;
    }

    /**
     * Ticket activity history, oldest-first (HTS-041, AC-2). 404 if the ticket does not exist so the
     * nested resource stays honest.
     */
    @Transactional(readOnly = true)
    public List<TicketActivity> activity(UUID ticketId) {
        get(ticketId);
        return activity.findByTicket_IdOrderBySeqAsc(ticketId);
    }

    // Append one activity row per changed field, all stamped with the same instant (HTS-041).
    private void recordActivity(Ticket ticket, List<TicketFieldChange> changes, String actorEmail) {
        if (changes.isEmpty()) {
            return;
        }
        String actor = EmailNormalizer.normalize(actorEmail);
        Instant now = clock.instant();
        for (TicketFieldChange change : changes) {
            activity.save(TicketActivity.of(ticket, actor, change, now));
        }
    }

    @Transactional
    public void delete(UUID id) {
        Ticket ticket = get(id);
        tickets.delete(ticket); // comments are removed by ON DELETE CASCADE (FR-K6)
    }

    private Team requireTeam(UUID teamId) {
        return teams.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found: " + teamId));
    }

    private Epic resolveEpic(UUID epicId) {
        if (epicId == null) {
            return null;
        }
        return epics.findById(epicId)
                .orElseThrow(() -> new NotFoundException("Epic not found: " + epicId));
    }

    // FR-E7/FR-K5: a set epic must be in the ticket's (possibly newly-chosen) team. Checked against
    // the request's team+epic, so a team change that keeps a now-cross-team epic is rejected here.
    private void requireEpicSameTeam(Team team, Epic epic) {
        if (epic != null && !java.util.Objects.equals(epic.getTeamId(), team.getId())) {
            throw new EpicTeamMismatchException();
        }
    }
}
