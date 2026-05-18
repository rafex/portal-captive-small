package com.portal.auth.application.port.out;

import com.portal.auth.domain.User;

import java.util.Optional;

public interface UserRepository {
    void save(User user);

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);
}
