package dev.pcvolkmer.onco.datamapper.fhir.ngs;

import dev.pcvolkmer.mv64e.model.SomaticNgsReport;
import dev.pcvolkmer.onco.datamapper.fhir.DeviceMapper;
import dev.pcvolkmer.onco.datamapper.fhir.ManyMapper;
import dev.pcvolkmer.onco.datamapper.fhir.builders.ReferenceBuilder;
import dev.pcvolkmer.onco.datamapper.fhir.filter.Filter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Type;
import org.jspecify.annotations.Nullable;

public class GenomicStudyDeviceMapper implements ManyMapper<SomaticNgsReport, Device> {

  private final ReferenceBuilder referenceBuilder;
  private final List<Filter<? extends Type>> filters;

  public GenomicStudyDeviceMapper(
      ReferenceBuilder referenceBuilder, List<Filter<? extends Type>> filters) {
    this.referenceBuilder = referenceBuilder;
    this.filters = filters;
  }

  @Override
  public List<Device> mapToMany(SomaticNgsReport sourceItem) {
    return getDeviceMappers(sourceItem).stream().map(mapper -> mapper.map(sourceItem)).toList();
  }

  @Override
  public void addManyToBundle(Bundle bundle, SomaticNgsReport sourceItem) {
    getDeviceMappers(sourceItem).forEach(mapper -> mapper.addToBundle(bundle, sourceItem));
  }

  private List<SingleDeviceMapper> getDeviceMappers(SomaticNgsReport report) {
    final var result = new ArrayList<SingleDeviceMapper>();

    if (null != report.getMetadata()) {
      for (int i = 0; i < report.getMetadata().size(); i++) {
        result.addAll(getDeviceMappers(report, i));
      }
    }

    return result;
  }

  List<SingleDeviceMapper> getDeviceMappers(SomaticNgsReport report, int index) {
    final var result = new ArrayList<SingleDeviceMapper>();

    if (index < 0 || null == report.getMetadata() || index >= report.getMetadata().size()) {
      return result;
    }

    final var metadata = report.getMetadata().get(index);
    if (null == metadata) {
      return result;
    }

    if (hasText(metadata.getSequencer())) {
      result.add(new SingleDeviceMapper(referenceBuilder, filters, index, "sequencing-device"));
    }

    if (hasText(metadata.getKitType()) || hasText(metadata.getKitManufacturer())) {
      // TODO: Wie unterscheiden zwischen sequencing kits und library prep kits?
      result.add(new SingleDeviceMapper(referenceBuilder, filters, index, "sequencing-kit"));
    }

    if (hasText(Objects.toString(metadata.getPipeline(), null))) {
      result.add(new SingleDeviceMapper(referenceBuilder, filters, index, "pipeline"));
    }

    return result;
  }

  private static boolean hasText(@Nullable String value) {
    return null != value && !value.isBlank();
  }

  static final class SingleDeviceMapper extends DeviceMapper<SomaticNgsReport> {

    private final int metadataIndex;
    private final String deviceKind;

    private SingleDeviceMapper(
        ReferenceBuilder referenceBuilder,
        List<Filter<? extends Type>> filters,
        int metadataIndex,
        String deviceKind) {
      super(referenceBuilder, filters);
      this.metadataIndex = metadataIndex;
      this.deviceKind = deviceKind;
    }

    @Override
    public String getPatientId(SomaticNgsReport item) {
      return item.getPatient().getId();
    }

    @Override
    public String getId(SomaticNgsReport item) {
      return String.format("%s_ngsdevice_%d_%s", item.getId(), metadataIndex, deviceKind);
    }

    boolean isSequencingDevice() {
      return "sequencing-device".equals(deviceKind);
    }

    @Override
    public Device map(SomaticNgsReport sourceItem) {
      final var metadata = sourceItem.getMetadata().get(metadataIndex);
      final var result = new Device();

      this.setResourceId(
          result,
          result.addIdentifier().setSystem(this.getSystem()).setValue(this.getId(sourceItem)));

      result.setMeta(
          new Meta()
              .setSource(this.fhirMetaSource)
              .addProfile(
                  "https://www.medizininformatik-initiative.de/fhir/ext/modul-mtb/StructureDefinition/genomic-study-device"));

      final var name =
          switch (deviceKind) {
            case "sequencing-device" -> metadata.getSequencer();
            case "sequencing-kit" -> metadata.getKitType();
            case "pipeline" -> metadata.getPipeline().toString();
            default -> throw new IllegalStateException("Unsupported device kind: " + deviceKind);
          };

      if (null != name && !name.isBlank()) {
        result.addDeviceName().setName(name).setType(Device.DeviceNameType.USERFRIENDLYNAME);
      }

      if ("sequencing-kit".equals(deviceKind)) {
        final var manufacturer = metadata.getKitManufacturer();
        if (null != manufacturer && !manufacturer.isBlank()) {
          result.setManufacturer(manufacturer);
        }
      }

      // TODO: Device.type

      return result;
    }
  }
}
