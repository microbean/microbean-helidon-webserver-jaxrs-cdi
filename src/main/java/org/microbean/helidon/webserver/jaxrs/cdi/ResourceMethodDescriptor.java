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

import java.lang.reflect.Modifier;

import java.util.Collections;
import java.util.Objects;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.util.regex.Pattern;

import javax.enterprise.inject.spi.Annotated;
import javax.enterprise.inject.spi.AnnotatedMethod;
import javax.enterprise.inject.spi.AnnotatedType;
import javax.enterprise.inject.spi.BeanManager;

import javax.ws.rs.Consumes;
import javax.ws.rs.HttpMethod;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;

import io.helidon.common.http.Http;
import io.helidon.common.http.MediaType;

public class ResourceMethodDescriptor<X> {

  static final Pattern pattern = Pattern.compile("^(\\s*/*\\s*)(.+)(\\s*/*\\s*)$");
  
  private final Http.RequestMethod httpMethod;
  
  private final Set<? extends MediaType> consumedMediaTypes;
  
  private final Set<? extends MediaType> producedMediaTypes;
  
  private final String path;
  
  private final AnnotatedMethod<? super X> resourceMethod;

  private final Set<Annotation> qualifiers;

  private final AnnotatedType<X> resourceClass;
  
  public ResourceMethodDescriptor(final AnnotatedType<X> resourceClass,
                                  final Set<Annotation> qualifiers,
                                  final AnnotatedMethod<? super X> resourceMethod,
                                  final String path,
                                  final Set<? extends MediaType> consumedMediaTypes,
                                  final Set<? extends MediaType> producedMediaTypes,
                                  final Http.RequestMethod httpMethod) {
    super();
    this.resourceClass = Objects.requireNonNull(resourceClass);
    this.qualifiers = qualifiers;
    this.resourceMethod = Objects.requireNonNull(resourceMethod);
    this.path = Objects.requireNonNull(path);
    if (consumedMediaTypes == null || consumedMediaTypes.isEmpty()) {
      this.consumedMediaTypes = Collections.singleton(MediaType.WILDCARD);
    } else {
      this.consumedMediaTypes = Collections.unmodifiableSet(new HashSet<>(consumedMediaTypes));
    }
    if (producedMediaTypes == null || producedMediaTypes.isEmpty()) {
      this.producedMediaTypes = Collections.singleton(MediaType.WILDCARD);
    } else {
      this.producedMediaTypes = Collections.unmodifiableSet(new HashSet<>(producedMediaTypes));
    }
    this.httpMethod = Objects.requireNonNull(httpMethod);
  }

  public AnnotatedType<X> getResourceClass() {
    return this.resourceClass;
  }

  public Set<Annotation> getQualifiers() {
    return this.qualifiers;
  }
  
  public AnnotatedMethod<? super X> getResourceMethod() {
    return this.resourceMethod;
  }
  
  public String getPath() {
    return this.path;
  }
  
  public Set<? extends MediaType> getConsumedMediaTypes() {
    return this.consumedMediaTypes;
  }
  
  public Set<? extends MediaType> getProducedMediaTypes() {
    return this.producedMediaTypes;
  }
  
  public Http.RequestMethod getHttpMethod() {
    return this.httpMethod;
  }
  
  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    final String path = this.getPath();
    if (path != null) {
      sb.append("@Path(").append("\"").append(path).append("\") ");
    }
    final Http.RequestMethod httpMethod = this.getHttpMethod();
    assert httpMethod != null;
    sb.append("@").append(httpMethod).append(" ");
    final Set<?> producedMediaTypes = this.getProducedMediaTypes();
    if (producedMediaTypes != null && !producedMediaTypes.isEmpty()) {
      sb.append("@Produces(");
      final Iterator<?> iterator = producedMediaTypes.iterator();
      assert iterator != null;
      while (iterator.hasNext()) {
        final Object thing = iterator.next();
        assert thing != null;
        sb.append("\"").append(thing).append("\"");
        if (iterator.hasNext()) {
          sb.append(", ");
        }
      }
      sb.append(") ");
    }
    final Set<?> consumedMediaTypes = this.getConsumedMediaTypes();
    if (consumedMediaTypes != null && !consumedMediaTypes.isEmpty()) {
      sb.append("@Consumes(");
      final Iterator<?> iterator = consumedMediaTypes.iterator();
      assert iterator != null;
      while (iterator.hasNext()) {
        final Object thing = iterator.next();
        assert thing != null;
        sb.append("\"").append(thing).append("\"");
        if (iterator.hasNext()) {
          sb.append(", ");
        }
      }
      sb.append(") ");
    }
    return sb.append(this.getResourceClass().getJavaClass().getName()).append("#").append(this.getResourceMethod()).toString();
  }
  
  public static final <X> ResourceMethodDescriptor<X> from(final BeanManager beanManager,
                                                           final String applicationPath,
                                                           final AnnotatedType<X> resourceClass,
                                                           final Set<Annotation> qualifiers,
                                                           final AnnotatedMethod<? super X> resourceMethod)
  {
    Objects.requireNonNull(beanManager);
    Objects.requireNonNull(resourceClass);
    Objects.requireNonNull(resourceMethod);
    ResourceMethodDescriptor<X> returnValue = null;
    if (!resourceMethod.isStatic() && Modifier.isPublic(resourceMethod.getJavaMember().getModifiers())) {
      final String path = getPath(applicationPath, resourceClass, resourceMethod);
      assert path != null;
      Produces produces = null;
      Consumes consumes = null;
      Http.Method httpMethod = null;
      final Set<Annotation> jaxRsMethodLevelAnnotations = ResourceClasses.find(beanManager, resourceMethod, ResourceMethodDescriptor::getJaxRsMethodLevelAnnotations);
      if (jaxRsMethodLevelAnnotations != null && !jaxRsMethodLevelAnnotations.isEmpty()) {
        for (final Annotation annotation : jaxRsMethodLevelAnnotations) {
          if (annotation instanceof Produces) {
            if (produces == null) {
              produces = (Produces)annotation;
            }
          } else if (annotation instanceof Consumes) {
            if (consumes == null) {
              consumes = (Consumes)annotation;
            }
          } else if (annotation instanceof Path) {
            // Skip it; already processed      
          } else {
            final String httpMethodString = getHttpMethod(annotation);
            if (httpMethodString != null) {
              httpMethod = Http.Method.valueOf(httpMethodString);
              assert httpMethod != null;
            }
          }
        }
      }
      if (httpMethod != null) {
        final Set<MediaType> producedMediaTypes = new HashSet<>();
        if (produces != null) {
          final String[] values = produces.value();
          assert values != null;
          for (String value : values) {
            assert value != null;
            producedMediaTypes.add(MediaType.parse(value.trim()));
          }
        }
        final Set<MediaType> consumedMediaTypes = new HashSet<>();
        if (consumes != null) {
          final String[] values = consumes.value();
          assert values != null;
          for (String value : values) {
            assert value != null;
            consumedMediaTypes.add(MediaType.parse(value.trim()));
          }
        }
        returnValue = new ResourceMethodDescriptor<>(resourceClass, qualifiers, resourceMethod, path, consumedMediaTypes, producedMediaTypes, httpMethod);
      }
    }
    return returnValue;
  }

  static final <X> String getPath(final String prefix, final AnnotatedType<X> resourceClass, final AnnotatedMethod<? super X> resourceMethod) {
    return getPath(getPath(prefix, resourceClass), resourceMethod);
  }
  
  private static final String getPath(final String prefix, final Annotated element) {
    return getPath(prefix, element == null ? (Path)null : element.getAnnotation(Path.class));
  }

  private static final String getPath(final String prefix, final Path path) {
    return getPath(prefix, path == null ? (String)null : pattern.matcher(path.value()).replaceAll("$2"));
  }

  private static final String getPath(final String prefix, final String path) {
    final String returnValue;
    if (path == null) {
      if (prefix == null) {
        returnValue = "";
      } else {
        returnValue = prefix;
      }
    } else if (prefix == null) {
      returnValue = path;
    } else {
      returnValue = new StringBuilder(prefix).append("/").append(path).toString();
    }
    return returnValue;
  }
  
  public static final Set<Annotation> getJaxRsMethodLevelAnnotations(final AnnotatedMethod<?> method) {
    Set<Annotation> returnValue = null;
    if (method != null) {
      final Set<? extends Annotation> annotations = method.getAnnotations();
      if (annotations != null && !annotations.isEmpty()) {
        returnValue = new HashSet<>();
        for (final Annotation annotation : annotations) {
          if (isJaxRsMethodLevelAnnotation(annotation)) {
            returnValue.add(annotation);
          }
        }
      }
    }
    if (returnValue == null || returnValue.isEmpty()) {
      returnValue = Collections.emptySet();
    } else {
      returnValue = Collections.unmodifiableSet(returnValue);
    }
    return returnValue;
  }
  
  private static final boolean isJaxRsMethodLevelAnnotation(final Annotation annotation) {
    return annotation instanceof Produces || annotation instanceof Consumes || getHttpMethod(annotation) != null;
  }
  
  private static final String getHttpMethod(final Annotation annotation) {
    final String returnValue;
    if (annotation == null) {
      returnValue = null;
    } else {
      final Class<?> annotationType = annotation.annotationType();
      if (annotationType == null) {
        returnValue = null;
      } else {
        final HttpMethod httpMethod = annotationType.getAnnotation(HttpMethod.class);
        if (httpMethod == null) {
          returnValue = null;
        } else {
          returnValue = httpMethod.value();
        }
      }
    }
    return returnValue;
  }
  
}
