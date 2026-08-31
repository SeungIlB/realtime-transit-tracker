package com.realtimetransit.backend.provider.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.provider.entity.VehicleRunObservationEntity;

@Mapper
public interface VehicleRunObservationMapper {

	long insertVehicleRunObservation(VehicleRunObservationEntity vehicleRunObservation);

	int deleteVehicleRunObservationsReceivedBefore(
			@Param("receivedBefore") Instant receivedBefore,
			@Param("limit") int limit);

	List<VehicleRunObservationEntity> findRecentVehicleRunObservations(
			@Param("lineId") UUID lineId,
			@Param("providerVehicleId") String providerVehicleId,
			@Param("observedAfter") Instant observedAfter,
			@Param("limit") int limit);
}
