package io.warp10.ext.matrixProfile;

import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

public class PleaseRenameFunction extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public PleaseRenameFunction(String name) {
    super(name);
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    //
    // Insert your function code here
    // 

    return stack;
  }
}
