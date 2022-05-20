//
//   Copyright 2021 - 2022  SenX S.A.S.
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//   See the License for the specific language governing permissions and
//   limitations under the License.
//

package io.warp10.ext.matrixprofile;

import java.util.HashMap;
import java.util.Map;

import io.warp10.warp.sdk.WarpScriptExtension;

public class MatrixProfileWarpScriptExtension extends WarpScriptExtension {

  //
  // Configurations
  // For each configuration, overriding rules follow when set:
  // default value < config value < capability value
  //

  public final static long DEFAULT_VALUE_MP_PROFILE_MAXSIZE = 50000L;
  public final static String CONFIG_MP_PROFILE_MAXSIZE = "mp.profile.maxsize";
  public final static String CAPNAME_MP_PROFILE_MAXSIZE = "mp.profile.maxsize.capname";

  public final static long DEFAULT_VALUE_MP_ABPROFILE_MAXSIZE = 10000L;
  public final static String CONFIG_MP_ABPROFILE_MAXSIZE = "mp.abprofile.maxsize";
  public final static String CAPNAME_MP_ABPROFILE_MAXSIZE = "mp.abprofile.maxsize.capname";

  //
  // Functions
  //

  private static final Map<String,Object> functions;

  static {
    functions = new HashMap<String,Object>();

    functions.put("MP.ATBUCKETINDEX", new ATBUCKETINDEX("MP.ATBUCKETINDEX"));
    functions.put("MP.PROFILE", new PROFILE("MP.PROFILE"));
    functions.put("MP.RPROFILE", new RPROFILE("MP.RPROFILE"));
    functions.put("MP.ABPROFILE", new ABPROFILE("MP.ABPROFILE"));
    functions.put("MP.FLUSS", new FLUSS("MP.FLUSS"));
  }

  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }
}
