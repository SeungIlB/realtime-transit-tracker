package com.realtimetransit.backend.transit.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.transit.entity.UpcomingArrivalEntity;

@Mapper
public interface ArrivalQueryMapper {

	List<UpcomingArrivalEntity> findUpcomingArrivalsByLineIdAndBoardingStopId(
			@Param("lineId") UUID lineId,
			@Param("directionId") UUID directionId,
			@Param("boardingStopId") UUID boardingStopId,
			@Param("asOf") Instant asOf,
			@Param("observedAfter") Instant observedAfter,
			@Param("limit") int limit);

	List<UpcomingArrivalEntity> findUpcomingArrivalsByLineIdAndBoardingStopIdAndAlightingStopId(
			@Param("lineId") UUID lineId,
			@Param("boardingStopId") UUID boardingStopId,
			@Param("alightingStopId") UUID alightingStopId,
			@Param("asOf") Instant asOf,
			@Param("observedAfter") Instant observedAfter,
			@Param("limit") int limit);
}
