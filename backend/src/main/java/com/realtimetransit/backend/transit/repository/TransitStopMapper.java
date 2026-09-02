package com.realtimetransit.backend.transit.repository;

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
			@Param("boardingStopId") UUID boardingStopId);

	void upsertTransitStop(TransitStopEntity stop);
}
