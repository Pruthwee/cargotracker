package org.eclipse.cargotracker.domain.model.voyage;

import java.io.Serializable;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.lang3.Validate;

@Embeddable
public class VoyageNumber implements Serializable {

  private static final long serialVersionUID = 1L;

  @Column(name = "voyage_number")
  @NotEmpty(message = "Voyage number cannot be empty.")
  private String number;

  public VoyageNumber() {
    // Nothing to initialize.
  }

  public VoyageNumber(String number) {
    Validate.notNull(number);

    this.number = number;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }

    if (o == null) {
      return false;
    }

    if (!(o instanceof VoyageNumber)) {
      return false;
    }

    return sameValueAs((VoyageNumber) o);
  }

  @Override
  public int hashCode() {
    return number.hashCode();
  }

  boolean sameValueAs(VoyageNumber other) {
    return other != null && this.number.equals(other.number);
  }

  @Override
  public String toString() {
    return number;
  }

  public String getIdString() {
    return number;
  }
}
