/*
 * Copyright (C) 2017-2018 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.expr.fn.impl;

import com.dremio.exec.expr.AggrFunction;
import com.dremio.exec.expr.annotations.Workspace;
import com.dremio.exec.expr.fn.FunctionErrorContext;
import io.netty.buffer.ArrowBuf;
import org.apache.arrow.vector.holders.BigIntHolder;
import org.apache.arrow.vector.holders.BitHolder;
import org.apache.arrow.vector.holders.DecimalHolder;
import org.apache.arrow.vector.holders.Float4Holder;
import org.apache.arrow.vector.holders.Float8Holder;
import org.apache.arrow.vector.holders.IntHolder;
import org.apache.arrow.vector.holders.NullableBigIntHolder;
import org.apache.arrow.vector.holders.NullableDecimalHolder;
import org.apache.arrow.vector.holders.NullableFloat8Holder;
import org.apache.arrow.vector.holders.VarCharHolder;

import com.dremio.exec.expr.SimpleFunction;
import com.dremio.exec.expr.annotations.FunctionTemplate;
import com.dremio.exec.expr.annotations.Output;
import com.dremio.exec.expr.annotations.Param;
import com.dremio.exec.expr.annotations.FunctionTemplate.FunctionScope;
import com.dremio.exec.expr.annotations.FunctionTemplate.NullHandling;
import com.dremio.exec.expr.fn.OutputDerivation;

import javax.inject.Inject;
import java.math.BigDecimal;

public class DecimalFunctions {

  @FunctionTemplate(names = {"equal", "==", "="},
          scope = FunctionTemplate.FunctionScope.SIMPLE,
          nulls = NullHandling.NULL_IF_NULL)
  public static class EqualsDecimalVsDecimal implements SimpleFunction {

    @Param DecimalHolder left;
    @Param DecimalHolder right;
    @Output
    BitHolder out;

    public void setup() {}

    public void eval() {
      out.value = left.scale == right.scale ?
              org.apache.arrow.vector.util.ByteFunctionHelpers.equal(left.buffer, left.start, left.start + 16, right.buffer, right.start, right.start + 16) :
              0;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castVARCHAR"}, scope = FunctionScope.SIMPLE, nulls= NullHandling.NULL_IF_NULL)
  public static class CastDecimalVarChar implements SimpleFunction {

    @Param
    DecimalHolder in;
    @Param
    BigIntHolder len;
    @Output
    VarCharHolder out;
    @Inject
    ArrowBuf buffer;

    @Override
    public void setup() {
    }

    @Override
    public void eval() {
      in.start = (in.start / (org.apache.arrow.vector.util.DecimalUtility.DECIMAL_BYTE_LENGTH));
      java.math.BigDecimal bd = org.apache.arrow.vector.util.DecimalUtility.getBigDecimalFromArrowBuf(in.buffer, in.start, in.scale);
      String istr = bd.toString();
      out.start = 0;
      out.end = Math.min((int)len.value, istr.length()); // truncate if target type has length smaller than that of input's string
      buffer = buffer.reallocIfNeeded(out.end);
      out.buffer = buffer;
      out.buffer.setBytes(0, istr.substring(0,out.end).getBytes());
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castFLOAT8"}, scope = FunctionScope.SIMPLE, nulls= NullHandling.NULL_IF_NULL)
  public static class CastDecimalFloat8 implements SimpleFunction {

    @Param
    DecimalHolder in;
    @Output
    Float8Holder out;

    @Override
    public void setup() {
    }

    @Override
    public void eval() {
      in.start = (in.start / (org.apache.arrow.vector.util.DecimalUtility.DECIMAL_BYTE_LENGTH));
      java.math.BigDecimal bd = org.apache.arrow.vector.util.DecimalUtility.getBigDecimalFromArrowBuf(in.buffer, in.start, in.scale);
      out.value = bd.doubleValue();
    }
  }

  public static void main(String[] args) {
    BigDecimal bd = new BigDecimal("99.0000");
    System.out.println(bd.toString());
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castDECIMAL"}, derivation = OutputDerivation.DecimalCast.class, nulls= NullHandling.NULL_IF_NULL)
  public static class CastVarCharDecimal implements SimpleFunction {

    @Param
    VarCharHolder in;
    @Param(constant = true)
    BigIntHolder precision;
    @Param(constant = true)
    BigIntHolder scale;
    @Output
    DecimalHolder out;
    @Inject
    ArrowBuf buffer;
    @Inject
    FunctionErrorContext errorContext;

    @Override
    public void setup() {
      buffer = buffer.reallocIfNeeded(16);
    }

    @Override
    public void eval() {
      String s = com.dremio.exec.expr.fn.impl.StringFunctionHelpers.toStringFromUTF8(in.start, in.end, in.buffer);
      java.math.BigDecimal bd = new java.math.BigDecimal(s).setScale((int) scale.value, java.math.RoundingMode.HALF_UP);
      try {
        org.apache.arrow.vector.util.DecimalUtility.writeBigDecimalToArrowBuf(bd, buffer, 0);
      } catch (RuntimeException e) {
        throw errorContext.error(e)
            .build();
      }
      out.buffer = buffer;
      out.precision = (int) precision.value;
      out.scale = (int) scale.value;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castDECIMAL"}, derivation = OutputDerivation.DecimalCast.class, nulls= NullHandling.NULL_IF_NULL)
  public static class CastIntDecimal implements SimpleFunction {

    @Param
    IntHolder in;
    @Param(constant = true)
    BigIntHolder precision;
    @Param(constant = true)
    BigIntHolder scale;
    @Output
    DecimalHolder out;
    @Inject
    ArrowBuf buffer;
    @Inject
    FunctionErrorContext errorContext;

    @Override
    public void setup() {
      buffer = buffer.reallocIfNeeded(16);
    }

    @Override
    public void eval() {
      java.math.BigDecimal bd = java.math.BigDecimal.valueOf(in.value).setScale((int) scale.value, java.math.RoundingMode.HALF_UP);
      try {
        org.apache.arrow.vector.util.DecimalUtility.writeBigDecimalToArrowBuf(bd, buffer, 0);
      } catch (RuntimeException e) {
        throw errorContext.error(e)
            .build();
      }
      out.buffer = buffer;
      out.precision = (int) precision.value;
      out.scale = (int) scale.value;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castDECIMAL"}, derivation = OutputDerivation.DecimalCast.class, nulls= NullHandling.NULL_IF_NULL)
  public static class CastBigIntDecimal implements SimpleFunction {

    @Param
    BigIntHolder in;
    @Param(constant = true)
    BigIntHolder precision;
    @Param(constant = true)
    BigIntHolder scale;
    @Output
    DecimalHolder out;
    @Inject
    ArrowBuf buffer;
    @Inject
    FunctionErrorContext errorContext;

    @Override
    public void setup() {
      buffer = buffer.reallocIfNeeded(16);
    }

    @Override
    public void eval() {
      java.math.BigDecimal bd = java.math.BigDecimal.valueOf(in.value).setScale((int) scale.value, java.math.RoundingMode.HALF_UP);
      try {
        org.apache.arrow.vector.util.DecimalUtility.writeBigDecimalToArrowBuf(bd, buffer, 0);
      } catch (RuntimeException e) {
        throw errorContext.error(e)
            .build();
      }
      out.buffer = buffer;
      out.precision = (int) precision.value;
      out.scale = (int) scale.value;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castDECIMAL"}, derivation = OutputDerivation.DecimalCast.class)
  public static class CastFloat4Decimal implements SimpleFunction {

    @Param
    Float4Holder in;
    @Param(constant = true)
    BigIntHolder precision;
    @Param(constant = true)
    BigIntHolder scale;
    @Output
    NullableDecimalHolder out;
    @Inject
    ArrowBuf buffer;
    @Inject
    FunctionErrorContext errorContext;

    @Override
    public void setup() {
      buffer = buffer.reallocIfNeeded(16);
    }

    @Override
    public void eval() {
      out.isSet = 1;
      java.math.BigDecimal bd = java.math.BigDecimal.valueOf(in.value).setScale((int) scale.value, java.math.RoundingMode.HALF_UP);
      try {
        org.apache.arrow.vector.util.DecimalUtility.writeBigDecimalToArrowBuf(bd, buffer, 0);
      } catch (RuntimeException e) {
        throw errorContext.error(e)
            .build();
      }
      out.buffer = buffer;
      out.precision = (int) precision.value;
      out.scale = (int) scale.value;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(names = {"castDECIMAL"}, derivation = OutputDerivation.DecimalCast.class, nulls= NullHandling.NULL_IF_NULL)
  public static class CastFloat8Decimal implements SimpleFunction {

    @Param
    Float8Holder in;
    @Param(constant = true)
    BigIntHolder precision;
    @Param(constant = true)
    BigIntHolder scale;
    @Output
    DecimalHolder out;
    @Inject
    ArrowBuf buffer;
    @Inject
    FunctionErrorContext errorContext;

    @Override
    public void setup() {
      buffer = buffer.reallocIfNeeded(16);
    }

    @Override
    public void eval() {
      java.math.BigDecimal bd = java.math.BigDecimal.valueOf(in.value).setScale((int) scale.value, java.math.RoundingMode.HALF_UP);
      try {
        org.apache.arrow.vector.util.DecimalUtility.writeBigDecimalToArrowBuf(bd, buffer, 0);
      } catch (RuntimeException e) {
        throw errorContext.error(e)
            .build();
      }
      out.buffer = buffer;
      out.precision = (int) precision.value;
      out.scale = (int) scale.value;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "sum", scope = FunctionTemplate.FunctionScope.POINT_AGGREGATE)
  public static class NullableDecimalSum implements AggrFunction {
    @Param NullableDecimalHolder in;
    @Workspace NullableFloat8Holder sum;
    @Workspace NullableBigIntHolder nonNullCount;
    @Output NullableFloat8Holder out;

    public void setup() {
      sum = new NullableFloat8Holder();
      sum.isSet = 1;
      sum.value = 0;
      nonNullCount = new NullableBigIntHolder();
      nonNullCount.isSet = 1;
      nonNullCount.value = 0;
    }
    public void add() {
      if (in.isSet != 0) {
        in.start = (in.start / (org.apache.arrow.vector.util.DecimalUtility.DECIMAL_BYTE_LENGTH));
        java.math.BigDecimal bd = org.apache.arrow.vector.util.DecimalUtility.getBigDecimalFromArrowBuf(in.buffer, in.start, in.scale);
        sum.value += bd.doubleValue();
        nonNullCount.value++;
      }
    }
    public void output() {
      if (nonNullCount.value > 0) {
        out.isSet = 1;
        out.value = sum.value;
      } else {
        // All values were null. Result should be null too
        out.isSet = 0;
      }
    }
    public void reset() {
      sum.value = 0;
      nonNullCount.value = 0;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "$sum0", scope = FunctionTemplate.FunctionScope.POINT_AGGREGATE)
  public static class NullableDecimalSumZero implements AggrFunction {
    @Param NullableDecimalHolder in;
    @Workspace NullableFloat8Holder sum;
    @Output NullableFloat8Holder out;

    public void setup() {
      sum = new NullableFloat8Holder();
      sum.isSet = 1;
      sum.value = 0;
    }
    public void add() {
      if (in.isSet == 1) {
        in.start = (in.start / (org.apache.arrow.vector.util.DecimalUtility.DECIMAL_BYTE_LENGTH));
        java.math.BigDecimal bd = org.apache.arrow.vector.util.DecimalUtility.getBigDecimalFromArrowBuf(in.buffer, in.start, in.scale);
        sum.value += bd.doubleValue();
      }
    }
    public void output() {
      out.isSet = 1;
      out.value = sum.value;
    }
    public void reset() {
      sum.value = 0;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "min", scope = FunctionTemplate.FunctionScope.POINT_AGGREGATE)
  public static class NullableDecimalMin implements AggrFunction {
    @Param NullableDecimalHolder in;
    @Workspace NullableFloat8Holder minVal;
    @Workspace NullableBigIntHolder nonNullCount;
    @Output NullableFloat8Holder out;

    public void setup() {
      minVal = new NullableFloat8Holder();
      minVal.isSet = 1;
      minVal.value = Double.MAX_VALUE;
      nonNullCount = new NullableBigIntHolder();
      nonNullCount.isSet = 1;
      nonNullCount.value = 0;
    }
    public void add() {
      if (in.isSet != 0) {
        nonNullCount.value = 1;
        in.start = (in.start / (org.apache.arrow.vector.util.DecimalUtility.DECIMAL_BYTE_LENGTH));
        java.math.BigDecimal bd = org.apache.arrow.vector.util.DecimalUtility.getBigDecimalFromArrowBuf(in.buffer, in.start, in.scale);
        double val = bd.doubleValue();
        if (val < minVal.value) {
          minVal.value = val;
        }
      }
    }
    public void output() {
      if (nonNullCount.value > 0) {
        out.isSet = 1;
        out.value = minVal.value;
      } else {
        // All values were null. Result should be null too
        out.isSet = 0;
      }
    }
    public void reset() {
      minVal.value = 0;
      nonNullCount.value = 0;
    }
  }

  @SuppressWarnings("unused")
  @FunctionTemplate(name = "max", scope = FunctionTemplate.FunctionScope.POINT_AGGREGATE)
  public static class NullableDecimalMax implements AggrFunction {
    @Param NullableDecimalHolder in;
    @Workspace NullableFloat8Holder maxVal;
    @Workspace NullableBigIntHolder nonNullCount;
    @Output NullableFloat8Holder out;

    public void setup() {
      maxVal = new NullableFloat8Holder();
      maxVal.isSet = 1;
      maxVal.value = -Double.MAX_VALUE;
      nonNullCount = new NullableBigIntHolder();
      nonNullCount.isSet = 1;
      nonNullCount.value = 0;
    }
    public void add() {
      if (in.isSet != 0) {
        nonNullCount.value = 1;
        in.start = (in.start / (org.apache.arrow.vector.util.DecimalUtility.DECIMAL_BYTE_LENGTH));
        java.math.BigDecimal bd = org.apache.arrow.vector.util.DecimalUtility.getBigDecimalFromArrowBuf(in.buffer, in.start, in.scale);
        double val = bd.doubleValue();
        if (val > maxVal.value) {
          maxVal.value = val;
        }
      }
    }
    public void output() {
      if (nonNullCount.value > 0) {
        out.isSet = 1;
        out.value = maxVal.value;
      } else {
        // All values were null. Result should be null too
        out.isSet = 0;
      }
    }
    public void reset() {
      maxVal.value = 0;
      nonNullCount.value = 0;
    }
  }
}


