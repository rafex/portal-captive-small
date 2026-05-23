package com.portal.auth.adapter.out.memory;

import com.portal.auth.application.port.out.UserRepository;
import com.portal.auth.domain.User;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryUserRepository implements UserRepository {
    private final Map<String, User> byId = new ConcurrentHashMap<>();
    private final Map<String, String> emailToId = new ConcurrentHashMap<>();
    private final Map<String, String> phoneToId = new ConcurrentHashMap<>();
    private final Map<String, String> ipToId = new ConcurrentHashMap<>();

    @Override
    public void save(User user) {
        byId.put(user.userId(), user);
        if (user.email() != null) {
            emailToId.put(user.email(), user.userId());
        }
        if (user.phone() != null) {
            phoneToId.put(user.phone(), user.userId());
        }
        if (user.deviceIp() != null) {
            ipToId.put(user.deviceIp(), user.userId());
        }
    }

    @Override
    public Optional<User> findByEmail(String email) {
        String id = emailToId.get(email);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByPhone(String phone) {
        String id = phoneToId.get(phone);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<User> findByDeviceIp(String deviceIp) {
        String id = ipToId.get(deviceIp);
        return id == null ? Optional.empty() : Optional.ofNullable(byId.get(id));
    }
}
