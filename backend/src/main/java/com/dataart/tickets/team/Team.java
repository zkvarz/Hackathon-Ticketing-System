package com.dataart.tickets.team;

import com.dataart.tickets.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Team (architecture.md §6). Name is stored trimmed (display form preserved) and is unique
 * case-insensitively via a functional index on lower(name) (FR-T4 / AMB-9).
 */
@Entity
@Table(name = "teams")
public class Team extends BaseEntity {

    @Column(nullable = false)
    private String name;

    protected Team() {
        // JPA
    }

    public Team(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
