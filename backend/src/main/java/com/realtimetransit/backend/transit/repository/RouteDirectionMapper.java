package com.realtimetransit.backend.transit.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.transit.entity.RouteDirectionEntity;

@Mapper
public interface RouteDirectionMapper {

	void upsertRouteDirection(RouteDirectionEntity direction);

	Optional<RouteDirectionEntity> findRouteDirectionByBusinessKey(
			@Param("lineId") UUID lineId,
			@Param("providerDirectionId") String providerDirectionId);

	int deactivateRouteDirectionsNotInProviderIds(
			@Param("lineId") UUID lineId,
			@Param("retainedProviderDirectionIds") List<String> retainedProviderDirectionIds);
}
