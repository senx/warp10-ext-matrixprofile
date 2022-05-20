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

package io.warp10.ext.matrixProfile;

import java.util.HashMap;
import java.util.Map;

import io.warp10.warp.sdk.WarpScriptExtension;

public class MatrixProfileExtension extends WarpScriptExtension {

  private static final Map<String,Object> functions;

  static {
    functions = new HashMap<String,Object>();

    functions.put("PROFILE", new PROFILE("PROFILE"));

    functions.put("RPROFILE", new RPROFILE("RPROFILE"));
    functions.put("ATBUCKETINDEX", new ATBUCKETINDEX("ATBUCKETINDEX"));
    functions.put("FLUSS", new FLUSS("FLUSS"));
  }

  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }
}
