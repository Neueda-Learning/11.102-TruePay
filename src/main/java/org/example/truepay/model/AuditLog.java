package org.example.truepay.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private UserProfile user;

	@Column(name = "transaction_id", nullable = false, length = 32)
	private String transactionId;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(nullable = false, length = 512)
	private String description;

	@Column(nullable = false)
	private Instant timestamp;

	@PrePersist
	public void prePersist() {
		if (timestamp == null) {
			timestamp = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public UserProfile getUser() {
		return user;
	}

	public void setUser(UserProfile user) {
		this.user = user;
	}

	public String getTransactionId() {
		return transactionId;
	}

	public void setTransactionId(String transactionId) {
		this.transactionId = transactionId;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(Instant timestamp) {
		this.timestamp = timestamp;
	}
}

