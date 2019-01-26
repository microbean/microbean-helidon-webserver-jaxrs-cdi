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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import java.util.function.Function;

import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;

public final class ResourceClasses {

  private ResourceClasses() {
    super();
  }

  public static final <X> Set<ResourceMethodDescriptor<X>> getResourceMethodDescriptors(final BeanManager beanManager,
                                                                                        final String applicationPath,
                                                                                        final AnnotatedType<X> type,
                                                                                        final Set<Annotation> qualifiers)
    throws ReflectiveOperationException {
    Set<ResourceMethodDescriptor<X>> returnValue = null;
    if (type != null) {
      final Set<AnnotatedMethod<? super X>> methods = type.getMethods();
      if (methods != null && !methods.isEmpty()) {
        returnValue = new HashSet<>();
        for (final AnnotatedMethod<? super X> method : methods) {
          if (method != null) {
            final ResourceMethodDescriptor<X> descriptor = ResourceMethodDescriptor.from(beanManager, applicationPath, type, qualifiers, method);
            if (descriptor != null) {
              returnValue.add(descriptor);
            }
          }
        }
      }
    }
    return returnValue;
  }

  public static final <X, T> T find(final BeanManager beanManager,
                                    final AnnotatedMethod<? super X> source,
                                    Function<? super AnnotatedMethod<? super X>, T> tester) {
    Objects.requireNonNull(beanManager);
    Objects.requireNonNull(source);
    Objects.requireNonNull(tester);
    T returnValue = tester.apply(source);
    if (returnValue == null) {
      final Set<AnnotatedType<? super X>> interfaces = new LinkedHashSet<>();
      final AnnotatedType<? super X> declaringType = source.getDeclaringType();
      assert declaringType != null;
      AnnotatedType<? super X> c = beanManager.createAnnotatedType(declaringType.getJavaClass().getSuperclass());
      AnnotatedMethod<? super X> m = null;
      while (c != null) {
        m = c.getMethods()
          .stream()
          .filter(am -> am.getJavaMember().getName().equals(source.getJavaMember().getName()) && am.getParameters().equals(source.getParameters()))
          .findAny() // should be zero or one
          .orElse(null);
        if (m != null) {
          returnValue = tester.apply(m);
          if (returnValue != null) {
            break;
          }
        }
        @SuppressWarnings("unchecked")
        final Class<? super X>[] cInterfaces = (Class<? super X>[])c.getJavaClass().getInterfaces();
        if (cInterfaces != null && cInterfaces.length > 0) {
          for (final Class<? super X> cInterface : cInterfaces) {
            interfaces.add(beanManager.createAnnotatedType(cInterface));
          }
        }
        final Class<? super X> superclass = c.getJavaClass().getSuperclass();
        if (superclass == null) {
          c = null;
        } else {
          c = beanManager.createAnnotatedType(superclass);
        }
      }

      if (returnValue == null && !interfaces.isEmpty()) {
        for (final AnnotatedType<? super X> iface : interfaces) {
          assert iface != null;
          m = iface.getMethods()
            .stream()
            .filter(am -> am.getJavaMember().getName().equals(source.getJavaMember().getName()) && am.getParameters().equals(source.getParameters()))
            .findAny() // should be zero or one
            .orElse(null);
          if (m != null) {
            returnValue = tester.apply(m);
            if (returnValue != null) {
              break;
            }
          }
        }
      }
      interfaces.clear();
      
    }
    return returnValue;
  }

}
