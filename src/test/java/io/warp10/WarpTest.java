//
// Copyright 2021 - 2022 SenX
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package io.warp10;

import io.warp10.ext.matrixProfile.MatrixProfileExtension;
import io.warp10.ext.matrixProfile.DEV_PROFILE;
import io.warp10.ext.matrixProfile.PROFILE;
import io.warp10.script.MemoryWarpScriptStack;
import io.warp10.script.WarpScriptLib;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.script.ext.inventory.InventoryWarpScriptExtension;
import io.warp10.script.ext.token.TokenWarpScriptExtension;
import org.junit.Test;
import io.warp10.standalone.Warp;

import java.io.File;
import java.io.FileWriter;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WarpTest {

  //private static String HOME = "/home/jenx/Softwares/warp10-dev/";
  private static String HOME = "/home/jenx/Softwares/warp10-standalone/";


  @Test
  public void test() throws Exception {
    StringBuilder props = new StringBuilder();

    props.append("warp.timeunits=us\n");
    WarpConfig.safeSetProperties(new StringReader(props.toString()));
    WarpScriptLib.register(new MatrixProfileExtension());

    MemoryWarpScriptStack stack = new MemoryWarpScriptStack(null, null);
    stack.maxLimits();

    stack.execMulti("100 'N' STORE\n" +
        "  [] [] [] [] \n" +
        "  [ 0.0 2 $N <% DUP RAND 2 * 1 - + %> F FOR ]\n" +
        "  MAKEGTS");
    stack.execMulti("[ SWAP NULL 99 1 100 ] BUCKETIZE 0 GET");
    Object gts = stack.pop();

    stack.push(gts);
    stack.push(10L);
    WarpScriptStackFunction DEV_PROFILE = new DEV_PROFILE("DEV_PROFILE");
    DEV_PROFILE.apply(stack);

    stack.push(gts);
    stack.push(10L);
    WarpScriptStackFunction PROFILE = new PROFILE("PROFILE");
    PROFILE.apply(stack);

    stack.execMulti("- VALUES 0 SWAP <% + %> FOREACH");
    stack.exec("DUP 1E-4 < ASSERT");
    stack.exec("DEPTH 1 == ASSERT");

    System.out.println(stack.dump(1));
  }

  //@Ignore
  @Test
  public void testWarp() throws Exception {

    //
    // Config
    //

    String default_conf_folder = HOME + "etc/conf.d";
    List<String> conf = Files.walk(Paths.get(default_conf_folder)).map(x -> x.toString()).filter(f -> f.endsWith(".conf")).collect(Collectors.toList());

    //
    // Additional or overwriting configurations
    //

    String extraConfPath = HOME + "etc/conf.d/99-extra.conf";
    FileWriter fr = new FileWriter(new File(extraConfPath));
    fr.write("warp.timeunits = us\n");

    //fr.write("warpscript.extension.bert = io.warp10.ext.nlp.BertExtension\n");
    //fr.write("warpscript.defaultcl.io.warp10.ext.nlp.BertExtension = true\n");


    //fr.write("warpscript.extension.shadow = zzz#io.warp10.script.ext.shadow.ShadowWarpScriptExtension\n");
    //fr.write("shadow.macro.NOOP = <% 00/ %>\n");

    //fr.write("warpscript.extension.ml = ml.MachineLearningPackage\n");
    //fr.write("ml.package.sandbox = sandbox:11T3mi3HaWV5E7u3vzlOhp8Tqt7yeKfcHcryu2MNqEYY\n");
    //fr.write("warpscript.defaultcl.ml.MachineLearningPackage = true\n");
    fr.close();
    conf.add(extraConfPath);

    //
    // Loging
    //

    System.setProperty("log4j.configuration", new File(HOME + "etc/log4j.properties").toURI().toString());
    //System.setProperty("sensision.server.port", "0");

    // Ignore system JNA
    //System.setProperty("jna.debug_load.jna", "true");
    //System.setProperty("jna.nosys", "true");
    //System.setProperty("jna.boot.library.path", "/home/jenx/Softwares/warp10-dev/lib/warp10-ext-bert-0.0.7-uberjar.jar!/com/sun/jna/linux-x86-64/");

    //
    // Load jars from lib folder (plugins and extensions)
    //

    List<File> jars = new ArrayList<File>();
    for (File f: new File(HOME + "lib").listFiles()) {
      if (f.getName().substring(f.getName().length() - 3).equals("jar")) {
        jars.add(f);
      }
    }

    URLClassLoader cl = (URLClassLoader) WarpScriptLib.class.getClassLoader();
    Method m = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
    m.setAccessible(true);

    for (int i = 0; i < jars.size(); i++) {
      URL url = jars.get(i).toURL();
      m.invoke(cl, url);
      System.out.println("Loading " + url.toString());
    }

    //
    // Start Warp 10
    //

    Thread t = new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Warp.main(conf.toArray(new String[conf.size()]));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
    t.start();

    //
    // Built in extensions
    //

    WarpScriptLib.register(new InventoryWarpScriptExtension());
    //WarpScriptLib.register(new HttpWarpScriptExtension());

    while(null == Warp.getKeyStore()) {}
    WarpScriptLib.register(new TokenWarpScriptExtension(Warp.getKeyStore())); //null exception, must be loaded with config file
    System.out.println("Loaded Token extension");

    //
    // Other extensions (add them as separate modules and put main as dep, then put these ext as dep of test)
    //

    //WarpScriptLib.register(new ArrowExtension());
    //WarpScriptLib.register(new ForecastExtension());
    WarpScriptLib.register(new MatrixProfileExtension());

    //
    // Let Warp10 run
    //

    t.join();
  }

}