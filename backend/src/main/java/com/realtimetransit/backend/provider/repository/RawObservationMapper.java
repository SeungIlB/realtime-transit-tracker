package com.realtimetransit.backend.provider.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.provider.entity.ProviderCollectionStatusEntity;
import com.realtimetransit.backend.provider.entity.RawObservationEntity;

@Mapper
public interface RawObservationMapper {

	long insertRawObservation(RawObservationEntity rawObservation);

	int deleteExpiredRawObservations(
			@Param("expiresAtOrBefore") Instant expiresAtOrBefore,
			@Param("limit") int limit);

	Optional<RawObservationEntity> findLatestByProviderIdAndEndpointAndRequestKey(
			@Param("providerId") long providerId,
			@Param("endpoint") String endpoint,
			@Param("requestKey") String requestKey);

	Optional<RawObservationEntity> findLatestSuccessfulUnexpiredByRequestIdentity(
			@Param("providerId") long providerId,
			@Param("endpoint") String endpoint,
			@Param("requestKey") String requestKey,
			@Param("asOf") Instant asOf);

	List<ProviderCollectionStatusEntity> findCollectionStatusesReceivedAfter(
			@Param("receivedAfter") Instant receivedAfter);
}
