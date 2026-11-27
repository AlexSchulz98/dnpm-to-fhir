package dev.pcvolkmer.onco.datamapper.fhir;

import dev.pcvolkmer.onco.datamapper.fhir.builders.ReferenceBuilder;
import dev.pcvolkmer.onco.datamapper.fhir.filter.Filter;
import java.util.List;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Type;

public abstract class ProcedureMapper<S> extends DnpmToFhirMapper<S, Procedure> {

  protected ProcedureMapper(
      ReferenceBuilder referenceBuilder, List<Filter<? extends Type>> filters) {
    super(referenceBuilder, filters);
  }

  @Override
  public String getFhirResourceType() {
    return "Procedure";
  }

  @Override
  public String getSystem() {
    return this.fhirProperties.identifierSystem("procedure-id");
  }
}
