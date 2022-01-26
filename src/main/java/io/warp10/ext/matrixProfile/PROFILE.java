//
//   Copyright 2019 - 2021  SenX S.A.S.
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

public class PROFILE extends NamedWarpScriptFunction implements WarpScriptStackFunction {
  
  public PROFILE(String name) {
    super(name);
  }

  private double getValue(GeoTimeSerie gts, int index) {
    return ((Number) GTSHelper.valueAtIndex(gts, index)).doubleValue();
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object o = stack.pop();

    if (!(o instanceof Long)) {
      throw new WarpScriptException(getName() + "expects a pattern size as second parameter.");
    }
    long k = ((Number) o).longValue();

    if (k < 2) {
      throw new WarpScriptException(getName() + " 's pattern size must be strictly greater than 1.");
    }

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

    // number of vectors
    int p = gts.size() - (int) k + 1;
    
    //
    // Means and Std of each vectors
    //

    // compute means and std of each vectors
    double[] means = new double[p];
    double[] stds = new double[p];

    // mean
    BigDecimal olderSample = BigDecimal.valueOf(getValue(gts, 0));
    BigDecimal sum = BigDecimal.ZERO;

    // std (welford algorithm)
    BigDecimal m = olderSample;
    BigDecimal new_m;
    BigDecimal s = BigDecimal.ZERO;
    
    for (int i = 0; i < p; i++) {

      if (0 == i){

        for (int j = 0; j < k; j++) {
          BigDecimal bd = BigDecimal.valueOf(getValue(gts, j));

          // mean
          sum = sum.add(bd);

          // std
          BigDecimal temp = bd.subtract(m);
          new_m = temp.divide(BigDecimal.valueOf(j + 1), BigDecimal.ROUND_HALF_UP).add(m);
          s = temp.multiply(bd.subtract(new_m)).add(s);

          m = new_m;
        }

        means[0] = sum.divide(BigDecimal.valueOf(k), BigDecimal.ROUND_HALF_UP).doubleValue();
        stds[0] = Math.sqrt(s.divide(BigDecimal.valueOf(k - 1), BigDecimal.ROUND_HALF_UP).doubleValue());

      } else {
        BigDecimal newerSample = BigDecimal.valueOf(getValue(gts, i + p - 1));

        // mean
        sum = sum.subtract(olderSample);
        sum = sum.add(newerSample);
        means[i] = sum.divide(BigDecimal.valueOf(k), BigDecimal.ROUND_HALF_UP).doubleValue();

        // welford std - remove a sample
        //  Mk-1 = Mk - (xk - Mk) / (k - 1)
        //  Sk-1 = Sk - (xk – Mk-1) * (xk – Mk)

        BigDecimal temp = olderSample.subtract(m);
        BigDecimal temp_m = temp.divide(BigDecimal.valueOf(k - 1), BigDecimal.ROUND_HALF_UP).add(m);
        s = s.subtract(temp.multiply(olderSample.subtract(temp_m)));

        // welford std - add a sample
        // Mk = Mk-1 + (xk – Mk-1) / k
        // Sk = Sk-1 + (xk – Mk-1) * (xk – Mk)

        temp = newerSample.subtract(temp_m);
        new_m = temp.divide(BigDecimal.valueOf(k), BigDecimal.ROUND_HALF_UP).add(temp_m);
        s = temp.multiply(newerSample.subtract(new_m)).add(s);

        // std
        stds[i] = Math.sqrt(s.divide(BigDecimal.valueOf(k - 1), BigDecimal.ROUND_HALF_UP).doubleValue());

        // update for next iteration
        olderSample = BigDecimal.valueOf(getValue(gts, i));
      }
    }
    
    //
    // Matrix profile
    // We compute it full row by full row to keep the space complexity linear
    //

    GeoTimeSerie res = gts.cloneEmpty(p);
    GTSHelper.rename(res, gts.getName() + "::profile");

    double[] dotCache = new double[p];
    double[] dotRowInit = new double[p];

    // loop on each row
    for (int i = 0; i < p; i++) {
      double rowMinValue = Double.MAX_VALUE;
      int rowMinIndex = -1;

      // loop on each col
      for (int j = 0; j < p; j++) {

        // dot product
        double dot = 0;
        if (0 == i) {

          for (int l = 0; l < k; l++) {
            dot += getValue(gts, i + l) * getValue(gts, j + l);
            dotRowInit[j] = dot;
          }

        } else {

          if (j > 0) {
            // using previous diagonal value
            dot = dotCache[j-1] - getValue(gts, i - 1) * getValue(gts, j - 1)
                    + getValue(gts, i + (int) k - 1) * getValue(gts, j + (int) k - 1);
          } else {
            // by transposition
            dot = dotRowInit[i];
          }
        }

        dotCache[j] = dot;

        // ignore diagonal
        if (j == i) {
          continue;
        }

        // distance
        double d = 1.0D - (dot - k * means[i] * means[j]) / k * stds[i] * stds[j];
        d = 2.0D * k * d;
        d = Math.sqrt(d);

        // compare and update min
        if (-1 == rowMinIndex || d < rowMinValue) {
          rowMinValue = d;
          rowMinIndex = j;

        }

        // resolve ties: closest index;
        // and in case of another ties its the lower index
        if (d == rowMinValue && Math.abs(j - i) < Math.abs(rowMinIndex - i)) {
          rowMinIndex = j;
        }

      }

      GTSHelper.setValue(gts, GTSHelper.tickAtIndex(gts, i)), GeoTimeSerie.NO_LOCATION, rowMinIndex, rowMinValue);
    }







    return stack;
  }
}
