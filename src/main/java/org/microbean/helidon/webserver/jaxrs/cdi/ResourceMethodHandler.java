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

import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.List;
import java.util.Set;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedParameter;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;

import io.helidon.webserver.Handler;
import io.helidon.webserver.ServerRequest;
import io.helidon.webserver.ServerResponse;

// Note: NOT a bean.
class ResourceMethodHandler<T> implements Handler {

  private final BeanManager beanManager;
  
  private final ResourceMethodDescriptor<T> descriptor;

  private final List<? extends BeanType<?>> parameterBeans;

  public ResourceMethodHandler(final BeanManager beanManager,
                               final ResourceMethodDescriptor<T> descriptor)
    throws ReflectiveOperationException
  {
    super();
    Objects.requireNonNull(beanManager);
    Objects.requireNonNull(descriptor);
    this.beanManager = beanManager;
    this.descriptor = descriptor;
    this.parameterBeans = introspect();
  }

  public ResourceMethodDescriptor<T> getResourceMethodDescriptor() {
    return this.descriptor;
  }
  
  @Override
  public void accept(final ServerRequest request, final ServerResponse response) {
    // TODO implement
    try {
      this.invoke();
    } catch (final ReflectiveOperationException reflectiveOperationException) {
      request.next(reflectiveOperationException);
    }
  }

  private final Object[] getParameterValues() {
    final Object[] returnValue;
    if (this.parameterBeans == null || this.parameterBeans.isEmpty()) {
      returnValue = null;
    } else {
      final int beansSize = this.parameterBeans.size();
      returnValue = new Object[beansSize];
      for (int i = 0; i < beansSize; i++) {
        returnValue[i] = getReference(this.parameterBeans.get(i));
      }
    }
    return returnValue;
  }
  
  private final void invoke() throws ReflectiveOperationException {
    final ResourceMethodDescriptor<T> descriptor = this.getResourceMethodDescriptor();
    final AnnotatedType<T> resourceClass = descriptor.getResourceClass();
    assert resourceClass != null;
    final Set<Annotation> qualifiers = descriptor.getQualifiers();
    final AnnotatedMethod<? super T> method = descriptor.getResourceMethod();
    assert method != null;
    if (this.parameterBeans == null || this.parameterBeans.isEmpty()) {
      method.getJavaMember().invoke(getReference(resourceClass.getJavaClass(), qualifiers));
    } else {
      method.getJavaMember().invoke(getReference(resourceClass.getJavaClass(), qualifiers),
                                    getParameterValues());
    }
    
    final Collection<Object> parameterValues;
    final List<? extends AnnotatedParameter<? super T>> parameters = method.getParameters();
    if (parameters == null || parameters.isEmpty()) {
      parameterValues = Collections.emptySet();
    } else {
      parameterValues = new ArrayList<>();
      for (final AnnotatedParameter<? super T> parameter : parameters) {
        assert parameter != null;
        final Type baseType = parameter.getBaseType();
        assert baseType != null;
        final Set<Annotation> parameterQualifiers = new HashSet<>(qualifiers == null ? Collections.emptySet() : qualifiers);
        final Collection<? extends Annotation> parameterAnnotations = parameter.getAnnotations();
        if (parameterAnnotations == null || parameterAnnotations.isEmpty()) {
          parameterQualifiers.add(Entity.Literal.INSTANCE);
        } else {
          for (final Annotation parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation != null && this.beanManager.isQualifier(parameterAnnotation.annotationType())) {
              parameterQualifiers.add(parameterAnnotation);
            }
          }
        }
        parameterValues.add(getReference(baseType, parameterQualifiers));
      }
    }
    if (parameterValues == null || parameterValues.isEmpty()) {
      method.getJavaMember().invoke(getReference(resourceClass.getJavaClass(), qualifiers));
    } else {
      method.getJavaMember().invoke(getReference(resourceClass.getJavaClass(), qualifiers), parameterValues.toArray(new Object[parameterValues.size()]));
    }
  }

  private final <X> List<BeanType<X>> introspect() throws ReflectiveOperationException {
    final ResourceMethodDescriptor<?> descriptor = this.getResourceMethodDescriptor();
    final AnnotatedType<?> resourceClass = descriptor.getResourceClass();
    assert resourceClass != null;
    final Set<Annotation> qualifiers = descriptor.getQualifiers();
    final AnnotatedMethod<?> method = descriptor.getResourceMethod();
    assert method != null;
    final List<BeanType<X>> parameterBeans;
    final List<? extends AnnotatedParameter<?>> parameters = method.getParameters();
    if (parameters == null || parameters.isEmpty()) {
      parameterBeans = Collections.emptyList();
    } else {
      parameterBeans = new ArrayList<>();
      for (final AnnotatedParameter<?> parameter : parameters) {
        assert parameter != null;
        final Type baseType = parameter.getBaseType();
        assert baseType != null;
        final Set<Annotation> parameterQualifiers = new HashSet<>(qualifiers == null ? Collections.emptySet() : qualifiers);
        final Collection<? extends Annotation> parameterAnnotations = parameter.getAnnotations();
        if (parameterAnnotations == null || parameterAnnotations.isEmpty()) {
          parameterQualifiers.add(Entity.Literal.INSTANCE);
        } else {
          for (final Annotation parameterAnnotation : parameterAnnotations) {
            if (parameterAnnotation != null && this.beanManager.isQualifier(parameterAnnotation.annotationType())) {
              parameterQualifiers.add(parameterAnnotation);
            }
          }
        }
        final Set<Bean<?>> beans = this.beanManager.getBeans(baseType, parameterQualifiers.toArray(new Annotation[parameterQualifiers.size()]));
        @SuppressWarnings("unchecked")
        final Bean<X> bean = (Bean<X>)this.beanManager.resolve(beans);
        parameterBeans.add(new BeanType<X>(bean, baseType));
      }
    }
    return parameterBeans;
  }

  private final <T> T getReference(final BeanType<T> beanType) {
    return this.getReference(beanType.bean, beanType.beanType);
  }
  
  private final <T> T getReference(final Bean<T> bean, final Type type) {
    @SuppressWarnings("unchecked")
    final T returnValue = (T)this.beanManager.getReference(bean, type, this.beanManager.createCreationalContext(bean));
    return returnValue;
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
  
}
