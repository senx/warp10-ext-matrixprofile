//
//   Copyright 2022  SenX S.A.S.
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

import io.warp10.WarpConfig;
import io.warp10.continuum.gts.GeoTimeSerie.TYPE;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.continuum.gts.GTSHelper;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStack.Macro;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.warp.sdk.Capabilities;

import java.math.BigDecimal;

/**
 * Compute the AB join in the sense of the matrix profile wrt a certain distance macro to two input gts
 */
public class ABPROFILE extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  // todo: optimize using STOMP as implemented for PROFILE

  public ABPROFILE(String name) {
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

    // custom macro
    Macro macro = null;
    if (o instanceof Macro) {
      macro = (Macro) o;
      o = stack.pop();
    }

    //
    // Mandatory params
    //

    if (!(o instanceof Long)) {
      throw new WarpScriptException(getName() + "expects a subsequence size (LONG) as third parameter.");
    }
    long k = ((Number) o).longValue();

    if (k < 2) {
      throw new WarpScriptException(getName() + " 's subsequence size must be strictly greater than 1.");
    }

    o = stack.pop();

    if (!(o instanceof GeoTimeSerie)) {
      throw new WarpScriptException(getName() + " expects a GTS as second parameter.");
    }

    GeoTimeSerie gts2 = (GeoTimeSerie) o;

    if (TYPE.DOUBLE != gts2.getType()) {
      throw new WarpScriptException(getName() + " can only be applied to GTS with values of type DOUBLE.");
    }

    if (!GTSHelper.isBucketized(gts2)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (gts2.size() != GTSHelper.getBucketCount(gts2)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    o = stack.pop();

    if (!(o instanceof GeoTimeSerie)) {
      throw new WarpScriptException(getName() + " expects a GTS as first parameter.");
    }

    GeoTimeSerie gts1 = (GeoTimeSerie) o;

    if (TYPE.DOUBLE != gts1.getType()) {
      throw new WarpScriptException(getName() + " can only be applied to GTS with values of type DOUBLE.");
    }

    if (!GTSHelper.isBucketized(gts1)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (gts1.size() != GTSHelper.getBucketCount(gts1)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    // maxsize check
    long maxsize = MatrixProfileWarpScriptExtension.DEFAULT_VALUE_MP_ABPROFILE_MAXSIZE;
    if (null != WarpConfig.getProperty(MatrixProfileWarpScriptExtension.CONFIG_MP_ABPROFILE_MAXSIZE)) {
      maxsize = Long.parseLong(WarpConfig.getProperty(MatrixProfileWarpScriptExtension.CONFIG_MP_ABPROFILE_MAXSIZE));
    }
    String capValue = Capabilities.get(stack, MatrixProfileWarpScriptExtension.CAPNAME_MP_ABPROFILE_MAXSIZE);
    if (null != capValue) {
      maxsize = Long.valueOf(capValue);
    }

    if (gts1.size() > maxsize || gts2.size() > maxsize) {
      throw new WarpScriptException("Max size limit for " + getName() + " reached. Consider lower the bucketcount. To raise this limit, use a capable token or contact an administrator.");
    }

    // make sure it is sorted
    GTSHelper.sort(gts1);
    GTSHelper.sort(gts2);

    // number of vectors
    int p1 = gts1.size() - (int) k + 1;
    int p2 = gts2.size() - (int) k + 1;

    if (p1 < 1 || p2 < 1) {
      throw new WarpScriptException(getName() + " requires the subsequence length to be lower than the number of buckets for both input gts.");
    }

    //
    // Means and Std of each vectors
    //

    // compute means and std of each vectors
    double[] means1 = null;
    double[] stds1 = null;
    double[] means2 = null;
    double[] stds2 = null;

    if (null == macro) {
      means1 = new double[p1];
      stds1 = new double[p1];

      for (int i = 0; i < p1; i++) {

        // standard
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal sumsq = BigDecimal.ZERO;

        // todo: this part can be optimised using running stats computation formulas

        for (int j = i; j < i + k; j++) {
          BigDecimal bd;
          bd = BigDecimal.valueOf(((Number) GTSHelper.valueAtIndex(gts1, j)).doubleValue());
          sum = sum.add(BigDecimal.valueOf(((Number) GTSHelper.valueAtIndex(gts1, j)).doubleValue()));
          sumsq = sumsq.add(bd.multiply(bd));
        }

        BigDecimal bdk = BigDecimal.valueOf(k);
        means1[i] = sum.divide(bdk, BigDecimal.ROUND_HALF_UP).doubleValue();
        double variance = sumsq.divide(bdk, BigDecimal.ROUND_HALF_UP).subtract(sum.multiply(sum).divide(bdk.multiply(bdk), BigDecimal.ROUND_HALF_UP)).doubleValue();
        stds1[i] = Math.sqrt(variance);
      }

      means2 = new double[p2];
      stds2 = new double[p2];

      for (int i = 0; i < p2; i++) {

        // standard
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal sumsq = BigDecimal.ZERO;

        // todo: this part can be optimised using running stats computation formulas

        for (int j = i; j < i + k; j++) {
          BigDecimal bd;
          bd = BigDecimal.valueOf(((Number) GTSHelper.valueAtIndex(gts2, j)).doubleValue());
          sum = sum.add(BigDecimal.valueOf(((Number) GTSHelper.valueAtIndex(gts2, j)).doubleValue()));
          sumsq = sumsq.add(bd.multiply(bd));
        }

        BigDecimal bdk = BigDecimal.valueOf(k);
        means2[i] = sum.divide(bdk, BigDecimal.ROUND_HALF_UP).doubleValue();
        double variance = sumsq.divide(bdk, BigDecimal.ROUND_HALF_UP).subtract(sum.multiply(sum).divide(bdk.multiply(bdk), BigDecimal.ROUND_HALF_UP)).doubleValue();
        stds2[i] = Math.sqrt(variance);
      }
    }

    // initialization
    long bucketspan1 = GTSHelper.getBucketSpan(gts1);
    long lastbucket1 = GTSHelper.getLastBucket(gts1) - bucketspan1 * (k - 1);
    GeoTimeSerie res = new GeoTimeSerie(lastbucket1, p1, bucketspan1, p1);

    // meta
    res.setMetadata(gts1.getMetadata());
    GTSHelper.rename(res, gts1.getName() + "::abprofile::" + gts2.getName());

    //
    // Fill res
    //

    for (int i = 0; i < p1; i++) {

      double dmin = Double.MAX_VALUE;
      double d;
      int argmin = -1;

      for (int j = 0; j < p2; j++) {

        if (null == macro) {

          //
          // Dot product
          //

          double dot = 0.0D;
          for (int r = 0; r < k; r++) {
            dot += getValue(gts1, i + r) * getValue(gts2, j + r);
          }

          // distance
          d = 1.0D - (dot - k * means1[i] * means2[j]) / (k * stds1[i] * stds2[j]);
          d = 2.0D * k * d;
          d = Math.sqrt(d);

        } else {

          stack.push(ATBUCKETINDEX.subsequence(gts1, (int) k ,i));
          stack.push(ATBUCKETINDEX.subsequence(gts2, (int) k ,j));
          stack.exec(macro);
          d = ((Number) stack.pop()).doubleValue();
        }

        if (d < dmin) {
          dmin = d;
          argmin = j;
        }
      }

      GTSHelper.setValue(res, GTSHelper.tickAtIndex(gts1, argmin), GeoTimeSerie.NO_LOCATION, argmin, dmin, false);
    }

    stack.push(res);

    return stack;
  }
}
