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

import io.warp10.WarpConfig;
import io.warp10.continuum.gts.GeoTimeSerie.TYPE;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.continuum.gts.GTSHelper;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;
import io.warp10.warp.sdk.Capabilities;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Compute the Matrix Profile GTS using the STOMP algorithm
 * Store the row argmin in the elevation (value is the row min)
 */
public class PROFILE extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public static final String GTS = "gts";
  public static final String SUBSEQUENCE_LENGTH = "sub.length";
  public static final String EXCLUSION_RADIUS = "excl.radius";
  public static final String SIMILARITY_MEASURE_MACRO = "macro";
  public static final String ROBUSTNESS = "robust";

  public PROFILE(String name) {
    super(name);
  }

  private double getValue(GeoTimeSerie gts, int index) {
    return ((Number) GTSHelper.valueAtIndex(gts, index)).doubleValue();
  }

  private int defaultExclusionRadius(long k) {
    return ((Double) Math.ceil(k * 0.25)).intValue();
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    //
    // Parameters
    //

    GeoTimeSerie gts;
    long k; // subsequence size
    int exclusionRadius;
    WarpScriptStack.Macro distance;
    boolean robust;

    //
    // Two type of signature:
    //  - a MAP (with optional arguments)
    //  - or two arguments, gts and subsequence length
    //

    Object o = stack.pop();
    if (o instanceof Map) {

      Map params = (Map) o;

      //
      // Mandatory params
      //

      gts = (GeoTimeSerie) params.get(GTS);
      if (null == gts) {
        throw new WarpScriptException(getName() + " requires parameter " + GTS);
      }

      if (null == params.get(SUBSEQUENCE_LENGTH)) {
        throw new WarpScriptException(getName() + " requires parameter " + SUBSEQUENCE_LENGTH);
      }
      k = (long) params.get(SUBSEQUENCE_LENGTH);

      //
      // Optional parameters
      //

      if (null == params.get(EXCLUSION_RADIUS)) {
        exclusionRadius = defaultExclusionRadius(k);

      } else {
        exclusionRadius = ((Number) params.get(EXCLUSION_RADIUS)).intValue();
      }

      // nullable
      distance = (WarpScriptStack.Macro) params.get(SIMILARITY_MEASURE_MACRO);

      if (null == params.get(ROBUSTNESS)) {
        robust = false;
      } else {
        robust = Boolean.TRUE.equals(params.get(ROBUSTNESS));
      }

    } else {

      //
      // Second input type
      //

      // default
      robust = false;
      distance = null;

      Object second = stack.pop();
      if (o instanceof Long && second instanceof Long) {

        exclusionRadius = ((Number) o).intValue();
        k = ((Number) second).longValue();

        Object third = stack.pop();

        if (!(third instanceof GeoTimeSerie)) {
          throw new WarpScriptException(getName() + " expects a GTS as first parameter.");
        }
        gts = (GeoTimeSerie) third;

      } else {

        if (!(o instanceof Long)) {
          throw new WarpScriptException(getName() + " expects a subsequence size (LONG) as second parameter.");
        }
        k = ((Number) o).longValue();
        exclusionRadius = defaultExclusionRadius(k);

        if (!(second instanceof GeoTimeSerie)) {
          throw new WarpScriptException(getName() + " expects a GTS as first parameter.");
        }
        gts = (GeoTimeSerie) second;
      }

    }

    //
    // Sanity checks
    //

    if (k < 2) {
      throw new WarpScriptException(getName() + " 's subsequence size must be strictly greater than 1.");
    }

    if (TYPE.DOUBLE != gts.getType()) {
      throw new WarpScriptException(getName() + " can only be applied to GTS with values of type DOUBLE.");
    }

    if (!GTSHelper.isBucketized(gts)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (gts.size() != GTSHelper.getBucketCount(gts)) {
      throw new WarpScriptException(getName() + " can only be applied to a GTS that is bucketized and filled.");
    }

    if (k >= GTSHelper.getBucketCount(gts)) {
      throw new WarpScriptException(getName() + " requires the subsequence length to be lower than the bucketcount.");
    }

    if (exclusionRadius < 0) {
      throw new WarpScriptException(getName() + " exclusion radios can not be negative.");
    }

    // maxsize check
    long maxsize = MatrixProfileWarpScriptExtension.DEFAULT_VALUE_MP_PROFILE_MAXSIZE;
    if (null != WarpConfig.getProperty(MatrixProfileWarpScriptExtension.CONFIG_MP_PROFILE_MAXSIZE)) {
      maxsize = Long.parseLong(WarpConfig.getProperty(MatrixProfileWarpScriptExtension.CONFIG_MP_PROFILE_MAXSIZE));
    }
    String capValue = Capabilities.get(stack, MatrixProfileWarpScriptExtension.CAPNAME_MP_PROFILE_MAXSIZE);
    if (null != capValue) {
      maxsize = Long.valueOf(capValue);
    }

    if (gts.size() > maxsize) {
      throw new WarpScriptException("Max size limit for " + getName() + " reached. Consider lower the bucketcount. To raise this limit, use a capable token or contact an administrator.");
    }

    // sorting
    GTSHelper.sort(gts);

    // number of vectors
    int p = gts.size() - (int) k + 1;

    //
    // Precompute Means and Std of each vectors
    //

    double[] means = null;
    double[] stds = null;

    if (null == distance) {
      means = new double[p];
      stds = new double[p];

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
    }

    //
    // Matrix profile
    // We compute it by traversing each diagonal of the upper triangle
    //

    // initialization
    long bucketspan = GTSHelper.getBucketSpan(gts);
    long lastbucket = GTSHelper.getLastBucket(gts) - bucketspan * (k - 1);
    GeoTimeSerie res = new GeoTimeSerie(lastbucket, p, bucketspan, p);

    // data
    long[] ticks = new long[p];
    double[] rowMinValue = new double[p];
    long[] rowMinIndex = new long[p];
    for (int i = 0; i < p; i++) {
      rowMinValue[i] = Double.MAX_VALUE;
      ticks[i] = lastbucket - (p - 1 - i) * bucketspan;
    }

    // optional data (robust case)
    double[] rowMin2Value = null;
    long[] rowMin2Index = null;
    if (robust) {
      rowMin2Value = new double[p];
      rowMin2Index = new long[p];
      for (int i = 0; i < p; i++) {
        rowMin2Value[i] = Double.MAX_VALUE;
      }
    }

    // meta
    res.setMetadata(gts.getMetadata());
    GTSHelper.rename(res, gts.getName() + "::profile");

    // loop
    int firstDiagNotInExclusionZone = exclusionRadius;
    for (int t = firstDiagNotInExclusionZone; t < p; t++) {

      // dot product variable use for one diagonal traversal
      double dot = 0.0D;

      for (int j = t; j < p; j++) {

        // distance
        double d;

        // working on row i and col j, both are incremented each iteration
        int i = j - t;

        if (null == distance) {
          // first row of the matrix: we compute dot product fully
          if (0 == i) {
            for (int l = 0; l < k; l++) {
              dot += getValue(gts, i + l) * getValue(gts, j + l);
            }

          } else {
            // other rows: we use previous diagonal dot product
            dot -= getValue(gts, i - 1) * getValue(gts, j - 1);
            dot += getValue(gts, i + (int) k - 1) * getValue(gts, j + (int) k - 1);
          }

          // distance
          d = 1.0D - (dot - k * means[i] * means[j]) / (k * stds[i] * stds[j]);
          d = 2.0D * k * d;
          d = Math.sqrt(d);

        } else {
          stack.push(ATBUCKETINDEX.subsequence(gts, (int) k ,i));
          stack.push(ATBUCKETINDEX.subsequence(gts, (int) k ,j));
          stack.exec(distance);

          d = ((Number) stack.pop()).doubleValue();
        }

        // compare and set
        // in case of tie: closest index since we see lower diagonal first
        if (d < rowMinValue[i]) {
          rowMinValue[i] = d;
          rowMinIndex[i] = j;
        } else if (robust && d < rowMin2Value[i]) {
          rowMin2Value[i] = d;
          rowMin2Index[i] = j;
        }

        // symmetrical
        if (d < rowMinValue[j]) {
          rowMinValue[j] = d;
          rowMinIndex[j] = i;
        } else if (robust && d < rowMin2Value[j]) {
          rowMin2Value[j] = d;
          rowMin2Index[j] = i;
        }
      }
    }

    try {
      res.reset(ticks, null, robust ? rowMin2Index : rowMinIndex, robust ? rowMin2Value : rowMinValue, p);
    } catch (IOException e) {
      throw new WarpScriptException(e);
    }

    stack.push(res);

    return stack;
  }
}
