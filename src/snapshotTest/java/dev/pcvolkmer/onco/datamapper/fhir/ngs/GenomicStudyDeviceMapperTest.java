package dev.pcvolkmer.onco.datamapper.fhir.ngs;

import dev.pcvolkmer.mv64e.model.Converter;
import dev.pcvolkmer.onco.datamapper.fhir.DnpmToFhirTest;
import dev.pcvolkmer.onco.datamapper.fhir.builders.DizUniMrReferenceBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GenomicStudyDeviceMapperTest extends DnpmToFhirTest {

  @ParameterizedTest
  @ValueSource(strings = {"ngsreports.json"})
  void shouldMapGenomicStudyDevice(String filename) throws IOException {
    var inputStream =
        Objects.requireNonNull(this.getClass().getClassLoader().getResourceAsStream(filename));
    var mtb = Converter.fromJsonString(new String(inputStream.readAllBytes()));

    final var mapper = new GenomicStudyDeviceMapper(new DizUniMrReferenceBuilder(), List.of());

    var fhir =
        mtb.getNgsReports().stream()
            .flatMap(report -> mapper.mapToMany(report).stream())
            .collect(Collectors.toList());

    verifyAll(fhir, filename);
  }
}
