package com.remondis.remap;

import java.beans.PropertyDescriptor;

/**
 * Interface for transformations that can be skipped on <code>null</code> input.
 *
 * @param <RS> Source field type.
 * @param <RD> Destination field type.
 */
abstract class SkipWhenNullTransformation<RS, RD> extends Transformation {

  SkipWhenNullTransformation(Mapping<?, ?> mapping, PropertyDescriptor sourceProperty,
      PropertyDescriptor destinationProperty) {
    super(mapping, sourceProperty, destinationProperty);
  }

  /**
   * Returns the skip when null configuration.
   *
   * @return Returns <code>true</code> if this transformation should be skipped when the input value is
   *         <code>null</code>, otherwise <code>false</code> is returned.
   */
  abstract boolean isSkipWhenNull();

  abstract Transform<RS, RD> getTransformation();
}
