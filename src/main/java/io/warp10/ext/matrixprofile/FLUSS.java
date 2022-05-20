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

import io.warp10.continuum.gts.GTSHelper;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

import java.io.IOException;

/**
 * Used for semantic segmentation.
 */
public class FLUSS extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  //todo: tests

  public FLUSS(String name) {
    super(name);
  }

  private double canonArcCurveValue(int n, int i) {
    double h = n / 2.0;
    double a = 1 / h;
    return h - (a * (i - h) * (i - h));
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object o = stack.pop();

    if (!(o instanceof Long)) {
      throw new WarpScriptException(getName() + "expects a subsequence size (LONG) as second parameter.");
    }
    long window_size = ((Number) o).longValue();

    if (window_size < 2) {
      throw new WarpScriptException(getName() + " 's subsequence size must be strictly greater than 1.");
    }

    o = stack.pop();

    if (!(o instanceof GeoTimeSerie)) {
      throw new WarpScriptException(getName() + " expects a GTS as first parameter.");
    }

    GeoTimeSerie gts = (GeoTimeSerie) o;

    if (GeoTimeSerie.TYPE.DOUBLE != gts.getType()) {
      throw new WarpScriptException(getName() + " can only be applied to GTS with values of type DOUBLE.");
    }

    if (!GTSHelper.isBucketized(gts)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (gts.size() != GTSHelper.getBucketCount(gts)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (!gts.hasElevations()) {
      throw new WarpScriptException(getName() + " can only be applied to a Matrix Profile GTS produced by PROFILE.");
    }

    //
    // Adapted from https://github.com/matrix-profile-foundation/matrixprofile/blob/master/matrixprofile/algorithms/regimes.py
    //

    int n = gts.size();
    double[] res = new double[n];
    for (int i = 0; i < n; i++) {
      res[i] = 0;
    }

    for (int i = 0; i < n; i++) {
      long mpi = GTSHelper.elevationAtIndex(gts, i);
      int small = (int) Math.min(i, mpi);
      int large = (int) Math.max(i, mpi);
      res[small + 1] = res[small + 1] + 1;
      res[large] = res[large] - 1;
    }

    // cumsum
    double cumsum = 0;
    for (int i = 0; i < n - window_size; i++) {
      cumsum += res[i];
      double val = cumsum / canonArcCurveValue(n, i);
      res[i] = Math.min(1.0D, val);
    }

    // correct head and tail
    for (int i = 0; i < window_size; i++) {
      res[i] = 1;
      res[n - i - 1] = 1;
    }

    GeoTimeSerie ret = gts.cloneEmpty(gts.size());
    GTSHelper.rename(ret, gts.getName() + "::fluss");
    try {
      ret.reset(GTSHelper.getTicks(gts), null, null, res, n);
    } catch (IOException e) {
      throw new WarpScriptException(e);
    }

    stack.push(ret);

    return stack;
  }
}
