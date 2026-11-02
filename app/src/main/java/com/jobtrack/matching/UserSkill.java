package com.jobtrack.matching;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One skill in a user's profile. {@code weight} is how often it appeared in the CV
 * (or 1 when typed in), and decides which skills count as the user's strongest.
 */
@Entity
@Table(name = "user_skill")
@IdClass(UserSkill.Key.class)
public class UserSkill {

	@Id
	@Column(name = "owner_id")
	private Long ownerId;

	@Id
	private String name;

	@Column(nullable = false)
	private int weight;

	@Column(nullable = false)
	private String source;

	protected UserSkill() {
		// for JPA
	}

	UserSkill(long ownerId, String name, int weight, String source) {
		this.ownerId = ownerId;
		this.name = name;
		this.weight = weight;
		this.source = source;
	}

	public String getName() {
		return name;
	}

	public int getWeight() {
		return weight;
	}

	public boolean isFromCv() {
		return "CV".equals(source);
	}

	public static class Key implements Serializable {

		private Long ownerId;

		private String name;

		public Key() {
		}

		public Key(Long ownerId, String name) {
			this.ownerId = ownerId;
			this.name = name;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Key key && Objects.equals(this.ownerId, key.ownerId)
					&& Objects.equals(this.name, key.name);
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.ownerId, this.name);
		}

	}

}
