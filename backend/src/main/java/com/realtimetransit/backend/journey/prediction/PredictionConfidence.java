package com.realtimetransit.backend.journey.prediction;

public enum PredictionConfidence {
	HIGH,
	MEDIUM,
	LOW,
	UNKNOWN;

	public static PredictionConfidence lowest(
			PredictionConfidence first,
			PredictionConfidence second) {
		return first.ordinal() >= second.ordinal() ? first : second;
	}
}
