package com.realtimetransit.backend.transit.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.transit.entity.StopPatternEntity;
import com.realtimetransit.backend.transit.entity.StopPatternStopEntity;
import com.realtimetransit.backend.transit.entity.StopPatternStopDetailEntity;

@Mapper
public interface StopPatternMapper {

	List<StopPatternEntity> findActiveStopPatternsByLineIdAndServiceDate(
			@Param("lineId") UUID lineId,
			@Param("serviceDate") LocalDate serviceDate);

	void upsertStopPattern(StopPatternEntity stopPattern);

	Optional<StopPatternEntity> findStopPatternByBusinessKey(
			@Param("lineId") UUID lineId,
			@Param("providerPatternId") String providerPatternId,
			@Param("validFrom") LocalDate validFrom);

	void upsertStopPatternStops(@Param("patternStops") List<StopPatternStopEntity> patternStops);

	int deleteStopPatternStopsNotInSequences(
			@Param("stopPatternId") UUID stopPatternId,
			@Param("retainedStopSequences") List<Integer> retainedStopSequences);

	int deactivateStopPatternsNotInBusinessKeys(
			@Param("lineId") UUID lineId,
			@Param("retainedPatterns") List<StopPatternEntity> retainedPatterns);

	List<StopPatternStopDetailEntity> findStopsByStopPatternId(
			@Param("stopPatternId") UUID stopPatternId);
}
