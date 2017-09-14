package com.remondis.remap;

import static com.remondis.remap.ReflectionUtil.*;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.tools.ant.types.Mapper;

import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.InvocationHandler;

/**
 * The {@link InvocationSensor} tracks get-method invocations on a proxy class and makes the invocation information
 * available to the {@link Mapper}.
 *
 * @author schuettec
 *
 */
class InvocationSensor<T> {

  private T proxyObject;

  private List<String> propertyNames = new LinkedList<>();

  InvocationSensor(Class<T> superType) {
    Enhancer enhancer = new Enhancer();
    enhancer.setSuperclass(superType);
    enhancer.setCallback(new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (isGetter(method)) {
          denyNoReturnType(method);
          // schuettec - Get property name from method and mark this property as called.
          String propertyName = toPropertyName(method);
          propertyNames.add(propertyName);
          // schuettec - Then return an appropriate default value.
          return nullOrDefaultValue(method.getReturnType());
        } else if (isObjectMethod(method)) {
          // schuettec - 08.02.2017 : Methods like toString, equals or hashcode are redirected to this invocation
          // handler.
          return invokeMethodProxySafe(method, this, args);
        } else {
          throw MappingException.notAGetter(method);
        }
      }

    });
    proxyObject = superType.cast(enhancer.create());
  }

  /**
   * @return Returns the proxy object get-method calls can be performed on.
   */
  T getSensor() {
    return proxyObject;
  }

  /**
   * @return Returns the tracked property names.
   */
  List<String> getTrackedPropertyNames() {
    return Collections.unmodifiableList(propertyNames);
  }

  /**
   * @return Returns <code>true</code> if there were at least one interaction with a property. Otherwise
   *         <code>false</code> is returned.
   *
   */
  boolean hasTrackedProperties() {
    return !propertyNames.isEmpty();
  }

  /**
   * Resets all tracked information.
   */
  void reset() {
    propertyNames.clear();
  }

  private void denyNoReturnType(Method method) {
    if (!hasReturnType(method)) {
      throw MappingException.noReturnTypeOnGetter(method);
    }
  }

  private static Object nullOrDefaultValue(Class<?> returnType) {
    if (returnType.isPrimitive()) {
      return defaultValue(returnType);
    } else {
      return null;
    }
  }

  private static boolean isObjectMethod(Method method) {
    return method.getDeclaringClass() == Object.class;
  }

}