/* -*- mode: Java; c-basic-offset: 2; indent-tabs-mode: nil; coding: utf-8-unix -*-
 *
 * Copyright © 2019 microBean.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.microbean.helidon.webserver.jaxrs.cdi;

import java.lang.annotation.Annotation;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.Default;
import javax.enterprise.inject.UnsatisfiedResolutionException;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.common.http.Reader;

import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

// Note: NOT a bean.
class ResourceMethodHandler<T> implements Handler {

  private final BeanManager beanManager;
  
  private final ResourceMethodDescriptor<T> descriptor;

  private final List<? extends BeanType<?>> parameterBeanTypes;

  private final BeanType<?> entityParameterBeanType;

  public ResourceMethodHandler(final BeanManager beanManager,
                               final ResourceMethodDescriptor<T> descriptor)
    throws ReflectiveOperationException
  {
    super();
    Objects.requireNonNull(beanManager);
    Objects.requireNonNull(descriptor);
    this.beanManager = beanManager;
    this.descriptor = descriptor;
    this.parameterBeanTypes = this.introspect();
    this.entityParameterBeanType = this.getEntityParameterBeanType();
  }

  private final BeanType<?> getEntityParameterBeanType() {
    BeanType<?> returnValue = null;
    for (final BeanType<?> beanType : this.parameterBeanTypes) {
      assert beanType != null;
      final Bean<?> bean = beanType.bean;
      assert bean != null;
      final Collection<?> qualifiers = bean.getQualifiers();
      if (qualifiers != null && qualifiers.contains(Entity.Literal.INSTANCE)) {
        returnValue = beanType;
        break;
      }
    }
    return returnValue;
  }

  public ResourceMethodDescriptor<T> getResourceMethodDescriptor() {
    return this.descriptor;
  }
  
  @Override
  public void accept(final ServerRequest request, final ServerResponse response) {
    // Register any readers; we could do this in a separate Handler but why?
    final BeanType<?> entityParameterBeanType = this.getEntityParameterBeanType();
    if (entityParameterBeanType != null && entityParameterBeanType.beanType instanceof Class) {
      // Helidon doesn't deal with types that aren't Classes :-(
      final Type readerType = new ParameterizedTypeImplementation(Reader.class, entityParameterBeanType.beanType);
      request.content().registerReader((Class<?>)entityParameterBeanType.beanType, getReference(readerType, entityParameterBeanType.bean.getQualifiers()));
    }
    Object returnValue = null;
    try {
      returnValue = this.invoke();
    } catch (final ReflectiveOperationException | RuntimeException exception) {
      request.next(exception);
    }
    if (void.class.equals(returnValue) || Void.class.equals(returnValue)) {
      // TODO: kind of a hack; if the returnValue is ACTUALLY VOID
      // itself, then we probably want to send 200 or whatever JAX-RS
      // says to do here.  For now just next it.
      request.next();
    } else if (returnValue == null) {
        // TODO: 404?
      response.send(returnValue);
    } else {
      response.send(returnValue);
    }
  }

  private final Object[] getParameterValues() {
    final Object[] returnValue;
    if (this.parameterBeanTypes == null || this.parameterBeanTypes.isEmpty()) {
      returnValue = null;
    } else {
      final int beansSize = this.parameterBeanTypes.size();
      returnValue = new Object[beansSize];
      for (int i = 0; i < beansSize; i++) {
        returnValue[i] = getReference(this.parameterBeanTypes.get(i));
      }
    }
    return returnValue;
  }
  
  private final Object invoke() throws ReflectiveOperationException {
    final ResourceMethodDescriptor<T> descriptor = this.getResourceMethodDescriptor();
    final AnnotatedType<T> resourceClass = descriptor.getResourceClass();
    assert resourceClass != null;
    final Set<Annotation> qualifiers = descriptor.getQualifiers();
    final AnnotatedMethod<? super T> annotatedMethod = descriptor.getResourceMethod();
    assert annotatedMethod != null;
    final Method method = annotatedMethod.getJavaMember();
    assert method != null;
    final Set<Annotation> newQualifiers = new HashSet<>(qualifiers);
    newQualifiers.add(new ResourceClass.Literal(resourceClass.getJavaClass()));
    newQualifiers.remove(Default.Literal.INSTANCE);
    final Object resourceInstance = getReference(resourceClass, newQualifiers);
    assert resourceInstance != null;
    final Object invocationReturnValue;
    if (this.parameterBeanTypes == null || this.parameterBeanTypes.isEmpty()) {
      invocationReturnValue = method.invoke(resourceInstance);
    } else {
      invocationReturnValue = method.invoke(resourceInstance, getParameterValues());
    }
    return invocationReturnValue;
  }

  private final <X> List<BeanType<X>> introspect() throws ReflectiveOperationException {
    final ResourceMethodDescriptor<?> descriptor = this.getResourceMethodDescriptor();
    final AnnotatedType<?> resourceClass = descriptor.getResourceClass();
    assert resourceClass != null;
    final Set<Annotation> qualifiers = descriptor.getQualifiers();    
    final AnnotatedMethod<?> method = descriptor.getResourceMethod();
    assert method != null;
    final List<BeanType<X>> parameterBeanTypes;
    final List<? extends AnnotatedParameter<?>> parameters = method.getParameters();
    if (parameters == null || parameters.isEmpty()) {
      parameterBeanTypes = Collections.emptyList();
    } else {
      parameterBeanTypes = new ArrayList<>();
      for (final AnnotatedParameter<?> parameter : parameters) {
        assert parameter != null;
        final Type baseType = parameter.getBaseType();
        assert baseType != null;
        final Set<Annotation> parameterQualifiers = new HashSet<>(qualifiers == null ? Collections.emptySet() : qualifiers);
        final Collection<? extends Annotation> parameterAnnotations = parameter.getAnnotations();
        if (parameterAnnotations == null || parameterAnnotations.isEmpty()) {
          parameterQualifiers.remove(Default.Literal.INSTANCE);
          parameterQualifiers.add(Entity.Literal.INSTANCE);
        } else {
          boolean defaultRemoved = false;
          for (final Annotation parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation != null && this.beanManager.isQualifier(parameterAnnotation.annotationType())) {
              if (!defaultRemoved) {
                parameterQualifiers.remove(Default.Literal.INSTANCE);
                defaultRemoved = true;
              }
              parameterQualifiers.add(parameterAnnotation);
            }
          }
        }
        final Set<Bean<?>> beans = this.beanManager.getBeans(baseType, parameterQualifiers.toArray(new Annotation[parameterQualifiers.size()]));
        if (beans == null || beans.isEmpty()) {
          throw new UnsatisfiedResolutionException(baseType + " " + parameterQualifiers);
        }
        @SuppressWarnings("unchecked")
        final Bean<X> bean = (Bean<X>)this.beanManager.resolve(beans);
        assert bean != null;
        parameterBeanTypes.add(new BeanType<X>(bean, baseType));
      }
    }
    return parameterBeanTypes;
  }

  private final <T> T getReference(final BeanType<T> beanType) {
    return this.getReference(beanType.bean, beanType.beanType);
  }
  
  private final <T> T getReference(final Bean<T> bean, final Type type) {
    @SuppressWarnings("unchecked")
    final T returnValue = (T)this.beanManager.getReference(bean, type, this.beanManager.createCreationalContext(bean));
    return returnValue;
  }

  private final <T> T getReference(final AnnotatedType<T> type, final Collection<? extends Annotation> qualifiers) {
    return getReference(type.getBaseType(), qualifiers);
  }
  
  private final <T> T getReference(final Type type, final Collection<? extends Annotation> qualifiers) {
    final Set<Bean<?>> beans = this.beanManager.getBeans(type, qualifiers.toArray(new Annotation[qualifiers.size()]));
    @SuppressWarnings("unchecked")
    final Bean<T> bean = (Bean<T>)this.beanManager.resolve(beans);
    return this.getReference(bean, type);
  }

  private static final class BeanType<T> {

    private final Bean<T> bean;

    private final Type beanType;

    private BeanType(final Bean<T> bean, final Type type) {
      super();
      this.bean = bean;
      this.beanType = type;
    }
    
  }
  
  private static final class ParameterizedTypeImplementation implements ParameterizedType {
  
    private final Type ownerType;

    private final Type rawType;

    private final Type[] actualTypeArguments;

    private final int hashCode;

    private ParameterizedTypeImplementation(final Class<?> rawType, final Type firstActualTypeArgument, final Type... actualTypeArguments) {
      this(null, rawType, firstActualTypeArgument, actualTypeArguments);
    }
    
    private ParameterizedTypeImplementation(final Type ownerType, final Class<?> rawType, final Type firstActualTypeArgument, final Type... actualTypeArguments) {
      super();
      this.ownerType = ownerType;
      this.rawType = Objects.requireNonNull(rawType);
      Objects.requireNonNull(firstActualTypeArgument);
      final Type[] allActualTypeArguments;
      if (actualTypeArguments == null || actualTypeArguments.length <= 0) {
        allActualTypeArguments = new Type[] { firstActualTypeArgument };
      } else {
        allActualTypeArguments = new Type[actualTypeArguments.length + 1];
        allActualTypeArguments[0] = firstActualTypeArgument;
        System.arraycopy(actualTypeArguments, 1, allActualTypeArguments, 0, actualTypeArguments.length);
      }      
      this.actualTypeArguments = allActualTypeArguments;
      this.hashCode = this.computeHashCode();
    }
    
    @Override
    public final Type getOwnerType() {
      return this.ownerType;
    }
    
    @Override
    public final Type getRawType() {
      return this.rawType;
    }
    
    @Override
    public final Type[] getActualTypeArguments() {
      return this.actualTypeArguments;
    }
    
    @Override
    public final int hashCode() {
      return this.hashCode;
    }

    private final int computeHashCode() {
      int hashCode = 17;
      
      final Object ownerType = this.getOwnerType();
      int c = ownerType == null ? 0 : ownerType.hashCode();
      hashCode = 37 * hashCode + c;
      
      final Object rawType = this.getRawType();
      c = rawType == null ? 0 : rawType.hashCode();
      hashCode = 37 * hashCode + c;
      
      final Type[] actualTypeArguments = this.getActualTypeArguments();
      c = Arrays.hashCode(actualTypeArguments);
      hashCode = 37 * hashCode + c;
      
      return hashCode;
    }
    
    @Override
    public final boolean equals(final Object other) {
      if (other == this) {
        return true;
      } else if (other instanceof ParameterizedType) {
        final ParameterizedType her = (ParameterizedType)other;
        
        final Object ownerType = this.getOwnerType();
        if (ownerType == null) {
          if (her.getOwnerType() != null) {
            return false;
          }
        } else if (!ownerType.equals(her.getOwnerType())) {
          return false;
        }
        
        final Object rawType = this.getRawType();
        if (rawType == null) {
          if (her.getRawType() != null) {
            return false;
          }
        } else if (!rawType.equals(her.getRawType())) {
          return false;
        }
        
        final Type[] actualTypeArguments = this.getActualTypeArguments();
        if (!Arrays.equals(actualTypeArguments, her.getActualTypeArguments())) {
          return false;
        }
        
        return true;
      } else {
        return false;
      }
    }

  }

}
