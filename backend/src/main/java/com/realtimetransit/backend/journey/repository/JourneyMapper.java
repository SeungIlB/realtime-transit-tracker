package com.realtimetransit.backend.journey.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.journey.entity.JourneySessionEntity;
import com.realtimetransit.backend.journey.entity.JourneyStopValidationEntity;

@Mapper
public interface JourneyMapper {

	void insertJourneySession(JourneySessionEntity journey);

	Optional<JourneySessionEntity> findJourneySessionById(@Param("id") UUID id);

	Optional<JourneyStopValidationEntity> validateJourneyStopsOnSameDirection(
			@Param("lineId") UUID lineId,
			@Param("directionId") UUID directionId,
			@Param("boardingStopId") UUID boardingStopId,
			@Param("alightingStopId") UUID alightingStopId);

	int updateJourneySessionStatus(
			@Param("id") UUID id,
			@Param("status") String status,
			@Param("updatedAt") Instant updatedAt);

	int expireJourneySessionsBefore(
			@Param("asOf") Instant asOf,
			@Param("updatedAt") Instant updatedAt,
			@Param("limit") int limit);
}
