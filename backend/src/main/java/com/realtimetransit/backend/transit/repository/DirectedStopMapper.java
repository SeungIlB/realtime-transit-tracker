package com.realtimetransit.backend.transit.repository;

import java.util.List;
import java.util.UUID;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.realtimetransit.backend.transit.entity.DirectedStopAssignmentEntity;

@Mapper
public interface DirectedStopMapper {

	void upsertDirectedStop(DirectedStopAssignmentEntity directedStop);

	int deleteDirectedStopsNotInSequences(
			@Param("directionId") UUID directionId,
			@Param("retainedStopSequences") List<Integer> retainedStopSequences);
}
