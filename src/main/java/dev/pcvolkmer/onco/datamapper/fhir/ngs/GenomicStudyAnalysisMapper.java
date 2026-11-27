package dev.pcvolkmer.onco.datamapper.fhir.ngs;

import dev.pcvolkmer.mv64e.model.SomaticNgsReport;
import dev.pcvolkmer.onco.datamapper.fhir.ManyMapper;
import dev.pcvolkmer.onco.datamapper.fhir.ProcedureMapper;
import dev.pcvolkmer.onco.datamapper.fhir.builders.ReferenceBuilder;
import dev.pcvolkmer.onco.datamapper.fhir.filter.Filter;
import dev.pcvolkmer.onco.datamapper.fhir.ngs.GenomicStudyDeviceMapper.SingleDeviceMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Type;

public class GenomicStudyAnalysisMapper implements ManyMapper<SomaticNgsReport, Procedure> {

  private final ReferenceBuilder referenceBuilder;
  private final List<Filter<? extends Type>> filters;
  private final GenomicStudyDeviceMapper deviceMapper;

  public GenomicStudyAnalysisMapper(
      ReferenceBuilder referenceBuilder, List<Filter<? extends Type>> filters) {
    this.referenceBuilder = referenceBuilder;
    this.filters = filters;
    this.deviceMapper = new GenomicStudyDeviceMapper(referenceBuilder, filters);
  }

  @Override
  public List<Procedure> mapToMany(SomaticNgsReport sourceItem) {
    return getAnalysisMappers(sourceItem).stream().map(mapper -> mapper.map(sourceItem)).toList();
  }

  @Override
  public void addManyToBundle(Bundle bundle, SomaticNgsReport sourceItem) {
    getAnalysisMappers(sourceItem)
        .forEach(
            mapper -> {
              mapper.devices.forEach(device -> device.addToBundle(bundle, sourceItem));
              mapper.addToBundle(bundle, sourceItem);
            });
  }

  private List<SingleAnalysisMapper> getAnalysisMappers(SomaticNgsReport report) {
    final var result = new ArrayList<SingleAnalysisMapper>();

    // TODO: ist jeder metadata entry eine separate analysis?
    if (null != report.getMetadata()) {
      for (int i = 0; i < report.getMetadata().size(); i++) {
        if (null != report.getMetadata().get(i)) {
          result.add(
              new SingleAnalysisMapper(
                  referenceBuilder, filters, i, deviceMapper.getDeviceMappers(report, i)));
        }
      }
    }

    // Keep report type even without metadata
    if (result.isEmpty() && null != report.getType()) {
      result.add(new SingleAnalysisMapper(referenceBuilder, filters, -1, List.of()));
    }

    return result;
  }

  private static final class SingleAnalysisMapper extends ProcedureMapper<SomaticNgsReport> {

    private final int metadataIndex;
    private final List<SingleDeviceMapper> devices;

    private SingleAnalysisMapper(
        ReferenceBuilder referenceBuilder,
        List<Filter<? extends Type>> filters,
        int metadataIndex,
        List<SingleDeviceMapper> devices) {
      super(referenceBuilder, filters);
      this.metadataIndex = metadataIndex;
      this.devices = devices;
    }

    @Override
    public String getPatientId(SomaticNgsReport item) {
      return item.getPatient().getId();
    }

    @Override
    public String getId(SomaticNgsReport item) {
      return String.format("%s_ngsanalysis_%d", item.getId(), metadataIndex);
    }

    @Override
    public Procedure map(SomaticNgsReport sourceItem) {
      final var result = new Procedure();

      this.setResourceId(
          result,
          result.addIdentifier().setSystem(this.getSystem()).setValue(this.getId(sourceItem)));

      result.setMeta(
          new Meta()
              .setSource(this.fhirMetaSource)
              .addProfile(
                  "https://www.medizininformatik-initiative.de/fhir/ext/modul-mtb/StructureDefinition/mii-pr-mtb-genomic-study-analysis"));

      result.setStatus(Procedure.ProcedureStatus.COMPLETED);

      result.setCategory(
          new CodeableConcept()
              .addCoding(
                  new Coding()
                      .setSystem("http://terminology.hl7.org/CodeSystem/observation-category")
                      .setCode("laboratory")));

      result.setSubject(this.getPatientReference(sourceItem));

      // Method Type
      final var methodType = mapMethodType(sourceItem);
      if (!methodType.isEmpty()) {
        result.addExtension(
            new Extension(
                "http://hl7.org/fhir/uv/genomics-reporting/StructureDefinition/genomic-study-analysis-method-type",
                methodType));
      }

      // Focus
      // TODO: Referenz auf Erkrankung?

      // Specimen
      // TODO: specimen

      // Reference Genome
      if (metadataIndex >= 0) {
        final var metadata = sourceItem.getMetadata().get(metadataIndex);
        final var referenceGenome = Objects.toString(metadata.getReferenceGenome(), null);
        if (null != referenceGenome && !referenceGenome.isBlank()) {
          result.addExtension(
              new Extension(
                  "http://hl7.org/fhir/uv/genomics-reporting/StructureDefinition/genomic-study-analysis-genome-build",
                  mapReferenceGenome(referenceGenome)));
        }
      }

      // Device References
      devices.forEach(device -> result.addExtension(mapDeviceExtension(sourceItem, device)));

      return result;
    }

    private Extension mapDeviceExtension(SomaticNgsReport report, SingleDeviceMapper device) {
      final var result =
          new Extension()
              .setUrl(
                  "http://hl7.org/fhir/uv/genomics-reporting/StructureDefinition/genomic-study-analysis-device");

      result.addExtension(new Extension("device", device.getReference(report)));

      if (device.isSequencingDevice()) {
        result.addExtension(
            new Extension(
                "function",
                new CodeableConcept()
                    .addCoding(
                        new Coding()
                            .setSystem(
                                "https://www.medizininformatik-initiative.de/fhir/ext/modul-mtb/CodeSystem/mii-cs-mtb-genomicanalysis-devicefunction")
                            .setCode("sequencing-device")
                            .setDisplay("Sequenziergerät"))));
      }

      // TODO: function von kits and pipelines

      return result;
    }
  }

  private static CodeableConcept mapMethodType(SomaticNgsReport sourceItem) {
    final var result = new CodeableConcept();
    final var type = sourceItem.getType();

    if (null == type || null == type.getCode()) {
      return result;
    }

    // FHIR IG ergänzt das genomics-reporting CodeSystem um ein MII-CS
    switch (type.getCode()) {
      case ARRAY:
        // genomics-reporting CodeSystem
        result.addCoding(
            new Coding()
                .setSystem(
                    "http://hl7.org/fhir/uv/genomics-reporting/CodeSystem/genomic-study-method-type-cs")
                .setCode("microarray")
                .setDisplay("Microarray"));
        break;

      case EXOME:
        // MII CodeSystem
        result.addCoding(
            new Coding()
                .setSystem(
                    "https://www.medizininformatik-initiative.de/fhir/ext/modul-mtb/CodeSystem/mii-cs-mtb-genomic-analysis-method-type")
                .setCode("whole-exom-sequencing")
                .setDisplay("Whole Exom Sequencing (WES)"));
        break;

      case GENOME_LONG_READ:
      case GENOME_SHORT_READ:
        // MII CodeSystem
        result.addCoding(
            new Coding()
                .setSystem(
                    "https://www.medizininformatik-initiative.de/fhir/ext/modul-mtb/CodeSystem/mii-cs-mtb-genomic-analysis-method-type")
                .setCode("whole-genom-sequencing")
                .setDisplay("Whole Genom Sequencing (WGS)"));
        result.setText(type.getCode().getValue());
        break;

      case KARYOTYPING:
        // genomics-reporting CodeSystem
        result.addCoding(
            new Coding()
                .setSystem(
                    "http://hl7.org/fhir/uv/genomics-reporting/CodeSystem/genomic-study-method-type-cs")
                .setCode("karyotyping")
                .setDisplay("Karyotyping"));
        break;

      case PANEL:
        // MII CodeSystem
        result.addCoding(
            new Coding()
                .setSystem(
                    "https://www.medizininformatik-initiative.de/fhir/ext/modul-mtb/CodeSystem/mii-cs-mtb-genomic-analysis-method-type")
                .setCode("gene-panel")
                .setDisplay("Genpanel"));
        break;

      case OTHER:
        // genomics-reporting CodeSystem
        result.addCoding(
            new Coding()
                .setSystem(
                    "http://hl7.org/fhir/uv/genomics-reporting/CodeSystem/genomic-study-method-type-cs")
                .setCode("other")
                .setDisplay("Other"));
        break;

      default:
        final var display = type.getDisplay();
        result.setText(null != display && !display.isBlank() ? display : type.getCode().getValue());
    }

    return result;
  }

  private static CodeableConcept mapReferenceGenome(String referenceGenome) {
    final var result = new CodeableConcept();

    switch (referenceGenome.toUpperCase()) {
      case "HG19", "GRCH37":
        result.addCoding(
            new Coding().setSystem("http://loinc.org").setCode("LA14029-5").setDisplay("GRCh37"));
        break;

      case "HG38", "GRCH38":
        result.addCoding(
            new Coding().setSystem("http://loinc.org").setCode("LA26806-2").setDisplay("GRCh38"));
        break;

      default:
        result.setText(referenceGenome);
    }

    return result;
  }
}
