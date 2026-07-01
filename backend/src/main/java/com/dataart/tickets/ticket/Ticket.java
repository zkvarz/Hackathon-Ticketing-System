package com.dataart.tickets.ticket;

import com.dataart.tickets.auth.User;
import com.dataart.tickets.common.BaseEntity;
import com.dataart.tickets.epic.Epic;
import com.dataart.tickets.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Ticket (architecture.md §6). Belongs to a team, optionally to an epic; carries a type, a
 * workflow state, a title/body, and server-set created/modified metadata. {@code createdBy} is
 * taken from the authenticated user and is immutable ({@code updatable=false}).
 *
 * <p>{@link #applyChanges} implements the field-level diffing required by AMB-3 / FR-K4: it only
 * mutates fields that actually changed and reports whether anything did, so the service can leave
 * {@code modifiedAt} untouched on a no-op save.
 */
@Entity
@Table(name = "tickets")
public class Ticket extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    // Nullable: a ticket need not belong to an epic (FR-E6). Same-team enforcement is HTS-021.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epic_id")
    private Epic epic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketState state;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    protected Ticket() {
        // JPA
    }

    public Ticket(Team team, Epic epic, TicketType type, TicketState state,
                  String title, String body, User createdBy) {
        this.team = team;
        this.epic = epic;
        this.type = type;
        this.state = state;
        this.title = title;
        this.body = body;
        this.createdBy = createdBy;
    }

    /**
     * Apply an incoming edit, mutating only the fields that differ from the current values. When
     * something actually changed, {@code modifiedAt} is advanced to {@code now} (AMB-3/FR-K4); an
     * edit that submits identical values leaves the timestamp untouched.
     *
     * @return {@code true} if any tracked field changed, {@code false} if it was a no-op.
     */
    public boolean applyChanges(Team newTeam, Epic newEpic, TicketType newType,
                                TicketState newState, String newTitle, String newBody,
                                java.time.Instant now) {
        boolean changed = false;
        if (!Objects.equals(team.getId(), newTeam.getId())) {
            team = newTeam;
            changed = true;
        }
        if (!Objects.equals(epicId(epic), epicId(newEpic))) {
            epic = newEpic;
            changed = true;
        }
        if (type != newType) {
            type = newType;
            changed = true;
        }
        if (state != newState) {
            state = newState;
            changed = true;
        }
        if (!title.equals(newTitle)) {
            title = newTitle;
            changed = true;
        }
        if (!body.equals(newBody)) {
            body = newBody;
            changed = true;
        }
        if (changed) {
            markModified(now);
        }
        return changed;
    }

    /**
     * Set the workflow state and advance {@code modifiedAt} unconditionally (HTS-027, FR-K7). Any
     * target state is accepted (no sequential constraint, FR-B6). Unlike {@link #applyChanges},
     * this always bumps the timestamp — a state PATCH is an explicit change request, so re-setting
     * the current state still advances {@code modifiedAt} (documented AMB-3 exception).
     */
    public void changeState(TicketState newState, java.time.Instant now) {
        this.state = newState;
        markModified(now);
    }

    private static UUID epicId(Epic epic) {
        return epic == null ? null : epic.getId();
    }

    public Team getTeam() {
        return team;
    }

    public UUID getTeamId() {
        return team.getId();
    }

    public Epic getEpic() {
        return epic;
    }

    public UUID getEpicId() {
        return epic == null ? null : epic.getId();
    }

    public String getEpicTitle() {
        return epic == null ? null : epic.getTitle();
    }

    public TicketType getType() {
        return type;
    }

    public TicketState getState() {
        return state;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public UUID getCreatedById() {
        return createdBy.getId();
    }

    public String getCreatedByEmail() {
        return createdBy.getEmail();
    }
}
