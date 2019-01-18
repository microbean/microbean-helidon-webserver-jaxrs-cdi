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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

import javax.inject.Qualifier;

import javax.enterprise.util.AnnotationLiteral;
import javax.enterprise.util.Nonbinding;

@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE, ElementType.PARAMETER })
public @interface ResourceClass {

  @Nonbinding Class<?> resourceClass();
  
  static final class Literal extends AnnotationLiteral<ResourceClass> implements ResourceClass {
    
    private static final long serialVersionUID = 1L;

    private final Class<?> resourceClass;

    Literal() {
      this(null);
    }
    
    Literal(final Class<?> resourceClass) {
      super();
      this.resourceClass = resourceClass;
    }

    @Override
    public Class<?> resourceClass() {
      return this.resourceClass;
    }
    
  }
}

