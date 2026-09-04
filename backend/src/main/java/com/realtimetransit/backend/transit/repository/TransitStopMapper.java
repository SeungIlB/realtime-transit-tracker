package com.realtimetransit.backend.transit.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.transit.entity.DirectedStopEntity;
import com.realtimetransit.backend.transit.entity.DestinationStopEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;

@Mapper
public interface TransitStopMapper {

	Optional<TransitStopEntity> findByProviderResourceId(
			@Param("providerId") long providerId,
			@Param("providerStopId") String providerStopId);

	Optional<TransitStopEntity> findById(@Param("id") UUID id);

	List<DirectedStopEntity> findActiveStopsByLineId(@Param("lineId") UUID lineId);

	List<DestinationStopEntity> findDestinationsAfterBoardingStop(
			@Param("lineId") UUID lineId,
			@Param("directionId") UUID directionId,
			@Param("boardingStopId") UUID boardingStopId);

	Optional<Boolean> canReachAlightingBeforeTerminal(
			@Param("lineId") UUID lineId,
			@Param("providerDirectionId") String providerDirectionId,
			@Param("boardingStopId") UUID boardingStopId,
			@Param("alightingStopId") UUID alightingStopId,
			@Param("terminalProviderStopId") String terminalProviderStopId);

	List<TransitStopEntity> findActiveStopsWithinCoordinateBounds(
			@Param("minLatitude") BigDecimal minLatitude,
			@Param("maxLatitude") BigDecimal maxLatitude,
			@Param("minLongitude") BigDecimal minLongitude,
			@Param("maxLongitude") BigDecimal maxLongitude,
			@Param("centerLatitude") BigDecimal centerLatitude,
			@Param("centerLongitude") BigDecimal centerLongitude,
			@Param("limit") int limit);

	void upsertTransitStop(TransitStopEntity stop);
}
