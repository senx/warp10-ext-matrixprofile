package io.warp10.ext.matrixProfile;

import java.util.HashMap;
import java.util.Map;

import io.warp10.warp.sdk.WarpScriptExtension;

public class PleaseRenameWarpScriptExtension extends WarpScriptExtension {
  
  private static final Map<String,Object> functions;
  
  static {
    functions = new HashMap<String,Object>();
    
    functions.put("RENAME ME", new io.warp10.ext.matrixProfile.PleaseRenameFunction("RENAME ME"));
  }
  
  @Override
  public Map<String, Object> getFunctions() {
    return functions;
  }
}
