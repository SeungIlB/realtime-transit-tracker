package com.realtimetransit.backend.journey.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.journey.entity.BoardingPredictionSnapshotEntity;

@Mapper
public interface BoardingPredictionMapper {

	void insertBoardingPredictionSnapshots(
			@Param("snapshots") List<BoardingPredictionSnapshotEntity> snapshots);

	List<BoardingPredictionSnapshotEntity> findLatestBoardingDecisionByJourneyId(
			@Param("journeyId") UUID journeyId,
			@Param("asOf") Instant asOf);

	int deleteExpiredPredictions(
			@Param("asOf") Instant asOf,
			@Param("limit") int limit);
}
