package com.realtimetransit.backend.journey.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.journey.entity.TravelerLocationObservationEntity;

@Mapper
public interface JourneyLocationMapper {

	long insertTravelerLocationObservation(TravelerLocationObservationEntity observation);

	Optional<TravelerLocationObservationEntity> findLatestLocationByJourneyId(
			@Param("journeyId") UUID journeyId,
			@Param("asOf") Instant asOf);

	int deleteExpiredLocations(@Param("asOf") Instant asOf);
}
