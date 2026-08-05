package org.example.truepay.model;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_limits")
public class PaymentLimit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserProfile user;

    @Column(nullable = false)
    private boolean dailyEnabled;

    @Column(precision = 19, scale = 2)
    private BigDecimal dailyLimit;

    @Column(nullable = false)
    private boolean monthlyEnabled;

    @Column(precision = 19, scale = 2)
    private BigDecimal monthlyLimit;

    @Column(nullable = false)
    private boolean perTransactionEnabled;

    @Column(precision = 19, scale = 2)
    private BigDecimal perTransactionLimit;

    public Long getId() {
        return id;
    }

    public UserProfile getUser() {
        return user;
    }

    public void setUser(UserProfile user) {
        this.user = user;
    }

    public boolean isDailyEnabled() {
        return dailyEnabled;
    }

    public void setDailyEnabled(boolean dailyEnabled) {
        this.dailyEnabled = dailyEnabled;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public boolean isMonthlyEnabled() {
        return monthlyEnabled;
    }

    public void setMonthlyEnabled(boolean monthlyEnabled) {
        this.monthlyEnabled = monthlyEnabled;
    }

    public BigDecimal getMonthlyLimit() {
        return monthlyLimit;
    }

    public void setMonthlyLimit(BigDecimal monthlyLimit) {
        this.monthlyLimit = monthlyLimit;
    }

    public boolean isPerTransactionEnabled() {
        return perTransactionEnabled;
    }

    public void setPerTransactionEnabled(boolean perTransactionEnabled) {
        this.perTransactionEnabled = perTransactionEnabled;
    }

    public BigDecimal getPerTransactionLimit() {
        return perTransactionLimit;
    }

    public void setPerTransactionLimit(BigDecimal perTransactionLimit) {
        this.perTransactionLimit = perTransactionLimit;
    }
}

