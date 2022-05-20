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

import io.warp10.continuum.gts.GeoTimeSerie.TYPE;
import io.warp10.continuum.gts.GeoTimeSerie;
import io.warp10.continuum.gts.GTSHelper;
import io.warp10.script.NamedWarpScriptFunction;
import io.warp10.script.WarpScriptException;
import io.warp10.script.WarpScriptStack;
import io.warp10.script.WarpScriptStackFunction;

/**
 * Helper function to get a subsequence GTS starting at a given index and with a given length
 */
public class ATBUCKETINDEX extends NamedWarpScriptFunction implements WarpScriptStackFunction {

  public ATBUCKETINDEX(String name) {
    super(name);
  }

  private static double getValue(GeoTimeSerie gts, int index) {
    return ((Number) GTSHelper.valueAtIndex(gts, index)).doubleValue();
  }

  @Override
  public Object apply(WarpScriptStack stack) throws WarpScriptException {

    Object o = stack.pop();

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

    // push result
    stack.push(subsequence(gts, (int) k, bucketIndex));

    return stack;
  }

  public static GeoTimeSerie subsequence(GeoTimeSerie gts, int k, int startIndex) throws WarpScriptException {
    GeoTimeSerie res;

    // initialization
    if (GTSHelper.isBucketized(gts)) {
      long bucketspan = GTSHelper.getBucketSpan(gts);
      long lastbucket = GTSHelper.tickAtIndex(gts, startIndex + k - 1);
      res = new GeoTimeSerie(lastbucket, k, bucketspan, k);

    } else {
      res = new GeoTimeSerie(k);
    }

    // meta
    res.setMetadata(gts.getMetadata());
    GTSHelper.rename(res, gts.getName() + "::subsequence::" + startIndex);

    //
    // Fill res
    //

    for (int i = 0; i < k; i++) {
      long tick = GTSHelper.tickAtIndex(gts,startIndex + i);
      long location = gts.hasLocations() ? GTSHelper.locationAtIndex(gts, startIndex + i) : GeoTimeSerie.NO_LOCATION;
      long elevation = gts.hasElevations() ? GTSHelper.elevationAtIndex(gts, startIndex + i) : GeoTimeSerie.NO_ELEVATION;
      Object value = getValue(gts, startIndex + i);

      GTSHelper.setValue(res, tick, location, elevation, value, false);
    }

    return res;
  }
}
