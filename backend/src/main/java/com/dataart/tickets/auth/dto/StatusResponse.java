package com.dataart.tickets.auth.dto;

/**
 * Minimal generic status body ({@code {"status": "..."}}) for endpoints that intentionally reveal
 * nothing more — e.g. forgot-password (no enumeration) and reset-password success (HTS-037).
 */
public record StatusResponse(String status) {
}
