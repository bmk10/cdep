/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/
package io.cdep.cdep.generator;

import io.cdep.cdep.BuildFindModuleFunctionTable;
import io.cdep.cdep.ResolvedManifests;
import io.cdep.cdep.ast.finder.FunctionTableExpression;
import io.cdep.cdep.resolver.ResolvedManifest;
import io.cdep.cdep.utils.CDepManifestYmlUtils;
import io.cdep.cdep.utils.Invariant;
import io.cdep.cdep.yml.CDepManifestYmlGenerator;
import io.cdep.cdep.yml.cdepmanifest.CDepManifestYml;
import net.java.quickcheck.QuickCheck;
import net.java.quickcheck.characteristic.AbstractCharacteristic;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Objects;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

public class TestCMakeGenerator {
  final private GeneratorEnvironment environment = new GeneratorEnvironment(
      new File("./test-files/TestCMakeGenerator/working"), null,
      null, null, false, false);

  @Test
  public void fuzzTest() {
    //for (int i = 0 ; i < 100; ++ i)
    QuickCheck.forAll(new CDepManifestYmlGenerator(), new AbstractCharacteristic<CDepManifestYml>() {
      @Override
      protected void doSpecify(CDepManifestYml any) throws Throwable {
        String capture = CDepManifestYmlUtils.convertManifestToString(any);
        CDepManifestYml readAny = CDepManifestYmlUtils.convertStringToManifest("fuzz-test.yml", capture);
        try {
          Invariant.pushErrorCollectionScope(false);
          BuildFindModuleFunctionTable builder = new BuildFindModuleFunctionTable();
          builder.addManifest(new ResolvedManifest(new URL("https://google.com"), readAny));
          FunctionTableExpression table = builder.build();
          String result = new CMakeGenerator(environment, table).create();
        } finally {
          Invariant.popErrorCollectionScope();
        }
      }
    });
  }

  @Test
  public void testBoost() throws Exception {
    BuildFindModuleFunctionTable builder = new BuildFindModuleFunctionTable();
    builder.addManifest(ResolvedManifests.boost().manifest);
    FunctionTableExpression table = builder.build();
    String result = new CMakeGenerator(environment, table).create();
    System.out.printf(result);
  }

  @Test
  public void testRequires() throws Exception {
    BuildFindModuleFunctionTable builder = new BuildFindModuleFunctionTable();
    builder.addManifest(ResolvedManifests.multipleRequires().manifest);
    FunctionTableExpression table = builder.build();
    String result = new CMakeGenerator(environment, table).create();
    System.out.printf(result);
    boolean headerOnly = result.contains("target_compile_features(${target} PUBLIC cxx_auto_type cxx_decltype )");
    boolean archive = result.contains("INTERFACE_COMPILE_FEATURES cxx_auto_type INTERFACE_COMPILE_FEATURES cxx_decltype");
    assert(headerOnly || archive);
  }

  @Test
  public void testCurl() throws Exception {
    BuildFindModuleFunctionTable builder = new BuildFindModuleFunctionTable();
    builder.addManifest(ResolvedManifests.zlibAndroid().manifest);
    builder.addManifest(ResolvedManifests.boringSSLAndroid().manifest);
    builder.addManifest(ResolvedManifests.curlAndroid().manifest);
    FunctionTableExpression table = builder.build();
    String script = new CMakeGenerator(environment, table).create();
    System.out.printf(script);
  }

  @Test
  public void testAllResolvedManifests() throws Exception {
    LinkedHashMap<String, String> expected = new LinkedHashMap<>();
    expected.put("admob", "Reference com.github.jomof:firebase/app:2.1.3-rev8 was not found, "
        + "needed by com.github.jomof:firebase/admob:2.1.3-rev8");
    expected.put("fuzz1", "Could not parse main manifest coordinate []");
    boolean unexpectedFailures = false;
    for (ResolvedManifests.NamedManifest manifest : ResolvedManifests.all()) {
      BuildFindModuleFunctionTable builder = new BuildFindModuleFunctionTable();
      if(Objects.equals(manifest.name, "curlAndroid"))
      {
        builder.addManifest(ResolvedManifests.zlibAndroid().manifest);
        builder.addManifest(ResolvedManifests.boringSSLAndroid().manifest);
      }
      builder.addManifest(manifest.resolved);
      String expectedFailure = expected.get(manifest.name);
      try {
        FunctionTableExpression table = builder.build();
        new CMakeGenerator(environment, table).generate();
        if (expectedFailure != null) {
          fail("Expected failure");
        }
      } catch (RuntimeException e) {
        if (expectedFailure == null || !expectedFailure.equals(e.getMessage())) {
          System.out.printf("expected.put(\"%s\", \"%s\")\n", manifest.name, e.getMessage());
          unexpectedFailures = true;
        }
      }
    }
    if (unexpectedFailures) {
      throw new RuntimeException("Unexpected failures. See console.");
    }
  }
}
