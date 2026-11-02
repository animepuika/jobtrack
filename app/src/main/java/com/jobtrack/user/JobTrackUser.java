package com.jobtrack.user;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in user as Spring Security sees it. It carries the database ID so
 * controllers can pass it to services without looking the user up again.
 */
public final class JobTrackUser implements UserDetails {

	private final long id;

	private final String username;

	private final String passwordHash;

	public JobTrackUser(long id, String username, String passwordHash) {
		this.id = id;
		this.username = username;
		this.passwordHash = passwordHash;
	}

	public long getId() {
		return id;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority("ROLE_USER"));
	}

}
