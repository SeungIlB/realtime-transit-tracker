package com.realtimetransit.backend.provider.repository;

import java.time.Instant;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.provider.entity.ArrivalPredictionObservationEntity;

@Mapper
public interface ArrivalPredictionObservationMapper {

	long insertArrivalPredictionObservation(
			ArrivalPredictionObservationEntity arrivalPredictionObservation);

	int deleteArrivalPredictionsReceivedBefore(
			@Param("receivedBefore") Instant receivedBefore,
			@Param("limit") int limit);
}
