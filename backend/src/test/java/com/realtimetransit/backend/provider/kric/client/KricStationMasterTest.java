package com.realtimetransit.backend.provider.kric.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KricStationMasterTest {

	private final KricStationMaster stationMaster = new KricStationMaster();

	@Test
	void loadsTheNewIncheonAndGimpoLinesFromTheKricSnapshot() {
		assertThat(stationMaster.findByLineCode("I1"))
				.hasSize(33)
				.first()
				.satisfies(station -> {
					assertThat(station.getOperatorCode()).isEqualTo("IC");
					assertThat(station.getStationCode()).isEqualTo("107");
				});
		assertThat(stationMaster.findByLineCode("I2")).hasSize(27);
		assertThat(stationMaster.findByLineCode("G1")).hasSize(10);
	}

	@Test
	void keepsSeohaeLineOperatorSplitAndOfficialCodeGap() {
		var stations = stationMaster.findByLineCode("WS");

		assertThat(stations).hasSize(21);
		assertThat(stations.subList(0, 9)).allMatch(station -> "KR".equals(station.getOperatorCode()));
		assertThat(stations.subList(9, 21)).allMatch(station -> "SW".equals(station.getOperatorCode()));
		assertThat(stations).extracting(station -> station.getStationCode())
				.contains("S20", "S22")
				.doesNotContain("S21");
	}
}
