package org.example.truepay.service;

import jakarta.servlet.http.HttpSession;
import org.example.truepay.model.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class SessionService {
    public static final String SESSION_USER_ID = "TRUEPAY_USER_ID";

    public Long requireUserId(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER_ID);
        if (value instanceof Long userId) {
            return userId;
        }
        throw new TruePayException(ErrorCode.UNAUTHORIZED, HttpStatus.UNAUTHORIZED, "Login required");
    }

    public void login(HttpSession session, Long userId) {
        session.setAttribute(SESSION_USER_ID, userId);
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }
}

