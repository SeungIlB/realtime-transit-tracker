package com.realtimetransit.backend.provider.service;

import com.realtimetransit.backend.provider.client.dto.ExternalArrival;
import com.realtimetransit.backend.transit.entity.AlightingStopStatus;
import com.realtimetransit.backend.transit.entity.TransitLineEntity;
import com.realtimetransit.backend.transit.entity.TransitStopEntity;

public interface SubwayStopConfirmationService {

	AlightingStopStatus confirmAlightingStop(
			TransitLineEntity line,
			TransitStopEntity boardingStop,
			TransitStopEntity alightingStop,
			ExternalArrival arrival);
}
