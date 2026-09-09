package com.realtimetransit.backend.journey.repository;

import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.journey.entity.TravelerProfileEntity;

@Mapper
public interface TravelerProfileMapper {

	void upsertTravelerProfile(TravelerProfileEntity profile);

	Optional<TravelerProfileEntity> findById(@Param("id") UUID id);

	Optional<TravelerProfileEntity> findByAnonymousKey(@Param("anonymousKey") UUID anonymousKey);

	UUID lockById(@Param("id") UUID id);

	int deleteUnusedProfilesBefore(
			@Param("retainedAfter") Instant retainedAfter,
			@Param("limit") int limit);
}
