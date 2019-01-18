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

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import java.util.function.Function;

import javax.ws.rs.Produces;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestReflection {

  public TestReflection() {
    super();
  }

  @Test
  public void testSomething() throws Exception {

    // Get the public void frobnicate() method.
    final Method getMethodFrobnicate = ConcreteFrobnicator.class.getMethod("frobnicate");
    assertNotNull(getMethodFrobnicate);

    // It is from AbstractFrobnicator and inherited by ConcreteFrobnicator.
    assertSame(AbstractFrobnicator.class, getMethodFrobnicate.getDeclaringClass());

    // Weirdly, the Method returned by getDeclaredMethod is not the
    // same.  A Method is immutable so I'd expect the same reference,
    // but whatever.  It is, of course, equal.
    final Method declaredGetMethodFrobnicate = AbstractFrobnicator.class.getDeclaredMethod("frobnicate");
    assertNotNull(declaredGetMethodFrobnicate);
    assertNotSame(getMethodFrobnicate, declaredGetMethodFrobnicate);
    assertEquals(getMethodFrobnicate, declaredGetMethodFrobnicate);

    // Of course ConcreteFrobnicator doesn't declare it so this should
    // fail.
    try {
      ConcreteFrobnicator.class.getDeclaredMethod("frobnicate");
      fail();
    } catch (final NoSuchMethodException expected) {

    }

    // Get the public void caturgiate() method.
    final Method getMethodCaturgiate = ConcreteFrobnicator.class.getMethod("caturgiate");
    assertNotNull(getMethodCaturgiate);

    // It is from ConcreteFrobnicator so this should work.
    assertSame(ConcreteFrobnicator.class, getMethodCaturgiate.getDeclaringClass());

    // Now use getDeclaredMethod to get the same thing.
    final Method declaredGetMethodCaturgiate = ConcreteFrobnicator.class.getDeclaredMethod("caturgiate");
    assertNotNull(declaredGetMethodCaturgiate);
    assertNotSame(getMethodCaturgiate, declaredGetMethodCaturgiate);
    assertEquals(getMethodCaturgiate, declaredGetMethodCaturgiate);

    // Let's verify that @Inherited doesn't work on annotations
    // applied to methods.  @Produces is defined to be @Inherited.
    assertNull(getMethodCaturgiate.getAnnotation(Produces.class));

    assertSame(AbstractFrobnicator.class, ConcreteFrobnicator.class.getSuperclass());
    assertSame(AbstractFrobnicator.class, getMethodCaturgiate.getDeclaringClass().getSuperclass());
    final Method interfaceCaturgiate = Frobnicator.class.getDeclaredMethod("caturgiate");
    assertNotNull(interfaceCaturgiate);
    assertNotNull(interfaceCaturgiate.getAnnotation(Produces.class));

    assertTrue(Frobnicator.class.isAssignableFrom(ConcreteFrobnicator.class));
    // Note that Class#getInterfaces() gives you interfaces that you directly implement.
    final Class<?>[] interfaces = ConcreteFrobnicator.class.getInterfaces();
    assertNotNull(interfaces);
    assertEquals(0, interfaces.length);    
  }

  @Test
  public void testFindMethod() throws ReflectiveOperationException {
    final Method spew = ConcreteFrobnicator.class.getMethod("spew");
    assertNotNull(spew);
    final Produces produces = ResourceClassSelector.find(spew, m -> m.getAnnotation(Produces.class));
    assertNotNull(produces);
    assertEquals("ConcreteFrobnicator", produces.value()[0]);
  }

  private static interface Frobnicator {

    public void frobnicate();

    @Produces
    public void caturgiate();

    @Produces("Frobnicator")
    public void spew();
    
  }

  private static abstract class AbstractFrobnicator implements Frobnicator {

    @Override    
    public void frobnicate() {
      
    }

    @Override
    public abstract void spew();
    
  }

  private static final class ConcreteFrobnicator extends AbstractFrobnicator {

    @Override
    public void caturgiate() {

    }

    @Override
    @Produces("ConcreteFrobnicator")
    public void spew() {

    }
    
  }
  
}
