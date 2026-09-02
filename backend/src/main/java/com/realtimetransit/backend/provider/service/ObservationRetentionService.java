package com.realtimetransit.backend.provider.service;

import java.time.Duration;

public interface ObservationRetentionService {

	int deleteExpiredRawObservations(int limit);

	int deleteOldVehicleRunObservations(Duration retention, int limit);

	int deleteOldArrivalPredictions(Duration retention, int limit);
}
