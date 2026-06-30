package com.dataart.tickets.epic;

import com.dataart.tickets.common.BaseEntity;
import com.dataart.tickets.team.Team;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Epic (architecture.md §6). Belongs to exactly one team, fixed at creation — the FK column is
 * {@code updatable=false} so the ORM never rewrites it (FR-E2). Title is non-empty trimmed;
 * description is optional.
 */
@Entity
@Table(name = "epics")
public class Epic extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "team_id", nullable = false, updatable = false)
    private Team team;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    protected Epic() {
        // JPA
    }

    public Epic(Team team, String title, String description) {
        this.team = team;
        this.title = title;
        this.description = description;
    }

    public Team getTeam() {
        return team;
    }

    public UUID getTeamId() {
        return team.getId();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
