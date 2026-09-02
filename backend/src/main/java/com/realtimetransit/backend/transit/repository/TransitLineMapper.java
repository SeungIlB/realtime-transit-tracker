package com.realtimetransit.backend.transit.repository;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.transit.entity.TransitLineEntity;

@Mapper
public interface TransitLineMapper {

	Optional<TransitLineEntity> findByProviderResourceId(
			@Param("providerId") long providerId,
			@Param("providerLineId") String providerLineId);

	Optional<TransitLineEntity> findById(@Param("id") java.util.UUID id);

	List<TransitLineEntity> searchActiveLines(
			@Param("providerId") long providerId,
			@Param("query") String query,
			@Param("limit") int limit);

	void upsertTransitLine(TransitLineEntity line);

	int deactivateTransitLinesNotInProviderLineIds(
			@Param("providerId") long providerId,
			@Param("retainedProviderLineIds") List<String> retainedProviderLineIds);
}
