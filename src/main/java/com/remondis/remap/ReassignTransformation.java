package com.remondis.remap;

import static com.remondis.remap.Properties.asString;
import static com.remondis.remap.ReflectionUtil.getCollector;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collector;

/**
 * The reassign operation maps a field to another field while the field names may differ. A reassign operation is only
 * allowed on fields of the same type.
 *
 * @author schuettec
 */
public class ReassignTransformation extends Transformation {

  private static final String REASSIGNING_MSG = "Reassigning %s\n           to %s";

  ReassignTransformation(Mapping<?, ?> mapping, PropertyDescriptor sourceProperty,
      PropertyDescriptor destinationProperty) {
    super(mapping, sourceProperty, destinationProperty);
  }

  protected static boolean isEqualTypes(Class<?> sourceType, Class<?> destinationType) {
    return sourceType.equals(destinationType);
  }

  private boolean isReferenceMapping(Class<?> sourceType, Class<?> destinationType) {
    return isEqualTypes(sourceType, destinationType) || ReflectionUtil.isWrapper(sourceType, destinationType)
        || ReflectionUtil.isWrapper(destinationType, sourceType);
  }

  @SuppressWarnings({
      "unchecked", "rawtypes"
  })
  @Override
  protected void performTransformation(PropertyDescriptor sourceProperty, Object source,
      PropertyDescriptor destinationProperty, Object destination) throws MappingException {
    Object sourceValue = readOrFail(sourceProperty, source);
    // Only if the source value is not null we have to perform the mapping
    if (sourceValue != null) {

      Class<?> sourceType = getSourceType();
      Class<?> destinationType = getDestinationType();

      Object destinationValue = null;
      destinationValue = _convert(sourceType, sourceValue, destinationType, destination);

      writeOrFail(destinationProperty, destination, destinationValue);
    }
  }

  private Object _convert(Class<?> sourceType, Object sourceValue, Class<?> destinationType, Object destination) {
    Object destinationValue;
    if (hasMapperFor(sourceType, destinationType)) {
      InternalMapper mapper = getMapperFor(this.sourceProperty, sourceType, this.destinationProperty, destinationType);
      destinationValue = mapper.map(sourceValue, null);
    } else if (isCollection(sourceType)) {
      Class<?> sourceCollectionType = findGenericTypeFromMethod(this.sourceProperty.getReadMethod());
      Class<?> destinationCollectionType = findGenericTypeFromMethod(this.destinationProperty.getReadMethod());
      destinationValue = convertCollection(sourceCollectionType, sourceValue, destinationCollectionType);
    } else {
      destinationValue = convertValueMapOver(sourceType, sourceValue, destinationType, destination);
    }
    return destinationValue;
  }

  private Object convertCollection(Class<?> sourceCollectionType, Object sourceValue,
      Class<?> destinationCollectionType) {
    return _convertCollection(sourceCollectionType, sourceValue, destinationCollectionType, 0);

  }

  private Object _convertCollection(Class<?> sourceCollectionType, Object sourceValue,
      Class<?> destinationCollectionType, int genericParameterDepth) {
    Collection collection = Collection.class.cast(sourceValue);
    Class<?> collectionType = findGenericTypeFromMethod(destinationProperty.getReadMethod(), genericParameterDepth);
    Collector collector = getCollector(collectionType);
    return collection.stream()
        .map(o -> {
          if (isCollection(o)) {
            return _convertCollection(sourceCollectionType, o, destinationCollectionType, genericParameterDepth + 1);
          } else {
            return convertValue(sourceCollectionType, o, destinationCollectionType);
          }
        })
        .collect(collector);
  }

  @SuppressWarnings({
      "unchecked", "rawtypes"
  })
  Object convertValue(Class<?> sourceType, Object sourceValue, Class<?> destinationType) {
    if (isReferenceMapping(sourceType, destinationType)) {
      return sourceValue;
    } else {
      // Object types must be mapped by a registered mapper before setting the value.
      InternalMapper delegateMapper = getMapperFor(sourceProperty, sourceType, destinationProperty, destinationType);
      return delegateMapper.map(sourceValue);
    }
  }

  @SuppressWarnings({
      "unchecked", "rawtypes"
  })
  Object convertValueMapOver(Class<?> sourceType, Object sourceValue, Class<?> destinationType,
      Object destinationValue) {
    if (isReferenceMapping(sourceType, destinationType)) {
      return sourceValue;
    } else {
      // Object types must be mapped by a registered mapper before setting the value.
      InternalMapper delegateMapper = getMapperFor(sourceProperty, sourceType, destinationProperty, destinationType);
      Object destinationValueMapped = readOrFail(destinationProperty, destinationValue);
      return delegateMapper.map(sourceValue, destinationValueMapped);
    }
  }

  /**
   * Finds the generic return type of a method in nested generics. For example this method returns {@link String} when
   * called on a method like <code>List&lt;List&lt;Set&lt;String&gt;&gt;&gt; get();</code>.
   *
   * @param method The method to analyze.
   * @return Returns the inner generic type.
   */
  static Class<?> findGenericTypeFromMethod(Method method) {
    ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
    Type type = null;
    while (parameterizedType != null) {
      type = parameterizedType.getActualTypeArguments()[0];
      if (type instanceof ParameterizedType) {
        parameterizedType = (ParameterizedType) type;
      } else {
        parameterizedType = null;
      }
    }
    return (Class<?>) type;
  }

  /**
   * Finds the generic return type of a method in nested generics. For example this method returns {@link String} when
   * called on a method like <code>List&lt;List&lt;Set&lt;String&gt;&gt;&gt; get();</code>.
   *
   * @param method The method to analyze.
   * @return Returns the inner generic type.
   */
  static Class<?> findGenericTypeFromMethod(Method method, int genericParameterDepth) {
    ParameterizedType parameterizedType = (ParameterizedType) method.getGenericReturnType();
    if (genericParameterDepth == 0) {
      return (Class<?>) parameterizedType.getRawType();
    }
    Type type = null;
    int i = 1;
    while (parameterizedType != null && i <= genericParameterDepth) {
      type = parameterizedType.getActualTypeArguments()[0];
      if (type instanceof ParameterizedType) {
        parameterizedType = (ParameterizedType) type;
      } else {
        parameterizedType = null;
      }
      i++;
    }
    if (parameterizedType == null) {
      return (Class<?>) type;
    } else {
      return (Class<?>) parameterizedType.getRawType();
    }
  }

  static boolean isCollection(Class<?> type) {
    return Collection.class.isAssignableFrom(type);
  }

  static boolean isCollection(Object collection) {
    return collection instanceof Collection;
  }

  @Override
  protected void validateTransformation() throws MappingException {
    // we have to check that all required mappers are known for nested mapping
    // if this transformation performs an object mapping, check for known mappers
    Class<?> sourceType = getSourceType();
    if (isMap(sourceType)) {
      throw MappingException.denyReassignOnMaps(getSourceProperty(), getDestinationProperty());
    }
    if (isCollection(sourceType)) {
      Class<?> sourceCollectionType = findGenericTypeFromMethod(sourceProperty.getReadMethod());
      Class<?> destinationCollectionType = findGenericTypeFromMethod(destinationProperty.getReadMethod());
      validateTypeMapping(getSourceProperty(), sourceCollectionType, getDestinationProperty(),
          destinationCollectionType);
    } else {
      Class<?> destinationType = getDestinationType();
      validateTypeMapping(getSourceProperty(), sourceType, getDestinationProperty(), destinationType);
    }
  }

  private void validateTypeMapping(PropertyDescriptor sourceProperty, Class<?> sourceType,
      PropertyDescriptor destinationProperty, Class<?> destinationType) {

    if (!isReferenceMapping(sourceType, destinationType)) {
      // Check if there is a registered mapper if required.
      getMapperFor(sourceProperty, sourceType, destinationProperty, destinationType);
    }
  }

  private boolean isMap(Class<?> sourceType) {
    return Map.class.isAssignableFrom(sourceType);
  }

  @Override
  public String toString(boolean detailed) {
    return String.format(REASSIGNING_MSG, asString(sourceProperty, detailed), asString(destinationProperty, detailed));

  }

}
