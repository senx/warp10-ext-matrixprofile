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

import io.warp10.continuum.gts.GeoTimeSerie.TYPE;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.continuum.gts.GTSHelper;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

import java.math.BigDecimal;

public class MP_ROW extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public MP_ROW(String name) {
    super(name);
  }

  private double getValue(GeoTimeSerie gts, int index) {
    return ((Number) GTSHelper.valueAtIndex(gts, index)).doubleValue();
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object o = stack.pop();

    //
    // Optional param
    //

    // value that multiply motif size to obtain exclusion zone radius
    double exclusionZoneRadiusRatio = 0.25;
    if (o instanceof Double) {
      exclusionZoneRadiusRatio = ((Number) o).doubleValue();
      o = stack.pop();
    }

    //
    // Mandatory params
    //

    if (!(o instanceof Long)) {
      throw new WarpScriptException(getName() + "expects a bucket index (LONG) as third parameter.");
    }
    int bucketIndex = ((Number) o).intValue();

    o = stack.pop();

    if (!(o instanceof Long)) {
      throw new WarpScriptException(getName() + "expects a subsequence size (LONG) as second parameter.");
    }
    long k = ((Number) o).longValue();

    if (k < 2) {
      throw new WarpScriptException(getName() + " 's subsequence size must be strictly greater than 1.");
    }

    o = stack.pop();

    if (!(o instanceof GeoTimeSerie)) {
      throw new WarpScriptException(getName() + " expects a GTS as first parameter.");
    }

    GeoTimeSerie gts = (GeoTimeSerie) o;

    if (TYPE.DOUBLE != gts.getType()) {
      throw new WarpScriptException(getName() + " can only be applied to GTS with values of type DOUBLE.");
    }

    if (!GTSHelper.isBucketized(gts)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (gts.size() != GTSHelper.getBucketCount(gts)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    // sorting
    GTSHelper.sort(gts);

    // number of vectors
    int p = gts.size() - (int) k + 1;

    if (bucketIndex >= p ){
      throw new WarpScriptException(getName() + " error: this bucket index can not start a subsequence of length " + k + ".");
    }

    //
    // Means and Std of each vectors
    //

    // compute means and std of each vectors
    double[] means = new double[p];
    double[] stds = new double[p];

    for (int i = 0; i < p; i++) {

      // standard
      BigDecimal sum = BigDecimal.ZERO;
      BigDecimal sumsq = BigDecimal.ZERO;

      // todo: this part can be optimised using running stats computation formulas

      for (int j = i; j < i + k; j++) {
        BigDecimal bd;
        bd = BigDecimal.valueOf(((Number) GTSHelper.valueAtIndex(gts, j)).doubleValue());
        sum = sum.add(BigDecimal.valueOf(((Number) GTSHelper.valueAtIndex(gts, j)).doubleValue()));
        sumsq = sumsq.add(bd.multiply(bd));
      }

      BigDecimal bdk = BigDecimal.valueOf(k);
      means[i] = sum.divide(bdk, BigDecimal.ROUND_HALF_UP).doubleValue();
      double variance = sumsq.divide(bdk, BigDecimal.ROUND_HALF_UP).subtract(sum.multiply(sum).divide(bdk.multiply(bdk), BigDecimal.ROUND_HALF_UP)).doubleValue();
      stds[i] = Math.sqrt(variance);
    }

    // initialization
    long bucketspan = GTSHelper.getBucketSpan(gts);
    long lastbucket = GTSHelper.getLastBucket(gts) - bucketspan * (k - 1);
    GeoTimeSerie res = new GeoTimeSerie(lastbucket, p, bucketspan, p);

    // meta
    res.setMetadata(gts.getMetadata());
    GTSHelper.rename(res, gts.getName() + "::mp.row::" + bucketIndex);

    //
    // Fill res
    //

    for (int i = 0; i < p; i++) {
      if (Math.abs(bucketIndex - i) <= k * exclusionZoneRadiusRatio) {
        continue;
      }

      //
      // Dot product
      //

      double dot = 0.0D;
      for (int j = 0; j < k; j++) {
        dot += getValue(gts, bucketIndex + j) * getValue(gts, i + j);
      }

      // distance
      double d = 1.0D - (dot - k * means[bucketIndex] * means[i]) / (k * stds[bucketIndex] * stds[i]);
      d = 2.0D * k * d;
      d = Math.sqrt(d);

      GTSHelper.setValue(res, GTSHelper.tickAtIndex(gts, i), GeoTimeSerie.NO_LOCATION, GeoTimeSerie.NO_ELEVATION, d, false);
    }

    stack.push(res);

    return stack;
  }
}
