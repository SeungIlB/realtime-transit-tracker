package com.realtimetransit.backend.provider.service;

import com.realtimetransit.backend.provider.dto.request.RawObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.VehicleRunObservationSaveRequest;
import com.realtimetransit.backend.provider.dto.request.ArrivalPredictionObservationSaveRequest;

public interface ObservationService {

	long saveRawObservation(RawObservationSaveRequest request);

	long saveVehicleRunObservation(VehicleRunObservationSaveRequest request);

	long saveArrivalPredictionObservation(ArrivalPredictionObservationSaveRequest request);
}
