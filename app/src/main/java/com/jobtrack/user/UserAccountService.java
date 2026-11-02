package com.jobtrack.user;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts and loads them for sign-in. Passwords are stored only as hashes.
 */
@Service
@Transactional
public class UserAccountService implements UserDetailsService {

	private final AppUserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final Clock clock;

	public UserAccountService(AppUserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.clock = clock;
	}

	public AppUser createUser(String username, String rawPassword) {
		if (username == null || username.isBlank()) {
			throw new IllegalArgumentException("Username must not be blank");
		}
		if (rawPassword == null || rawPassword.length() < 8) {
			throw new IllegalArgumentException("Password must be at least 8 characters");
		}
		String name = username.strip();
		if (this.users.findByUsername(name).isPresent()) {
			throw new IllegalArgumentException("User " + name + " already exists");
		}
		return this.users.save(new AppUser(name, this.passwordEncoder.encode(rawPassword), Instant.now(this.clock)));
	}

	@Transactional(readOnly = true)
	public Optional<AppUser> findByUsername(String username) {
		return this.users.findByUsername(username);
	}

	@Transactional(readOnly = true)
	public long count() {
		return this.users.count();
	}

	@Override
	@Transactional(readOnly = true)
	public JobTrackUser loadUserByUsername(String username) {
		AppUser user = this.users.findByUsername(username)
			.orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
		return new JobTrackUser(user.getId(), user.getUsername(), user.getPasswordHash());
	}

}
