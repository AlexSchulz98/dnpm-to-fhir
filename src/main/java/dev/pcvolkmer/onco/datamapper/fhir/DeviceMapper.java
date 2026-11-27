package dev.pcvolkmer.onco.datamapper.fhir;

import dev.pcvolkmer.onco.datamapper.fhir.builders.ReferenceBuilder;
import dev.pcvolkmer.onco.datamapper.fhir.filter.Filter;
import java.util.List;
import org.hl7.fhir.r4.model.Device;
import org.hl7.fhir.r4.model.Type;

public abstract class DeviceMapper<S> extends DnpmToFhirMapper<S, Device> {

  protected DeviceMapper(ReferenceBuilder referenceBuilder, List<Filter<? extends Type>> filters) {
    super(referenceBuilder, filters);
  }

  @Override
  public String getFhirResourceType() {
    return "Device";
  }

  @Override
  public String getSystem() {
    return this.fhirProperties.identifierSystem("device-id");
  }
}
