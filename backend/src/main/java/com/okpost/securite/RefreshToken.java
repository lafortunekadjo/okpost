package com.okpost.securite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** SHA-256 du jeton : le jeton lui-même n'est jamais stocké. */
    @Column(name = "jeton_hash", nullable = false, unique = true)
    private String jetonHash;

    @Column(name = "expire_le", nullable = false)
    private Instant expireLe;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(UUID userId, String jetonHash, Instant expireLe) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.jetonHash = jetonHash;
        this.expireLe = expireLe;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getJetonHash() { return jetonHash; }
    public Instant getExpireLe() { return expireLe; }
}
