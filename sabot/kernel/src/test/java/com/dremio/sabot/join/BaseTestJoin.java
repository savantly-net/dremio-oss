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
package com.dremio.sabot.join;

import static com.dremio.sabot.Fixtures.NULL_BIGINT;
import static com.dremio.sabot.Fixtures.NULL_VARCHAR;
import static com.dremio.sabot.Fixtures.t;
import static com.dremio.sabot.Fixtures.th;
import static com.dremio.sabot.Fixtures.tr;

import java.util.Arrays;
import java.util.List;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.IntVector;
import org.apache.calcite.rel.core.JoinRelType;
import org.junit.Test;

import com.dremio.common.logical.data.JoinCondition;
import com.dremio.common.types.TypeProtos.MinorType;
import com.dremio.common.types.Types;
import com.dremio.exec.physical.base.PhysicalOperator;
import com.dremio.exec.record.BatchSchema.SelectionVectorMode;
import com.dremio.exec.record.VectorAccessible;
import com.dremio.exec.record.VectorContainer;
import com.dremio.sabot.BaseTestOperator;
import com.dremio.sabot.Fixtures;
import com.dremio.sabot.Fixtures.DataRow;
import com.dremio.sabot.Fixtures.HeaderRow;
import com.dremio.sabot.Fixtures.Table;
import com.dremio.sabot.Generator;
import com.dremio.sabot.join.hash.EmptyGenerator;
import com.dremio.sabot.op.spi.DualInputOperator;
import com.google.common.collect.ImmutableList;

import io.airlift.tpch.GenerationDefinition.TpchTable;
import io.airlift.tpch.TpchGenerator;

public abstract class BaseTestJoin extends BaseTestOperator {

  public static class JoinInfo {
    public final Class<? extends DualInputOperator> clazz;
    public final PhysicalOperator operator;

    public <T extends DualInputOperator> JoinInfo(Class<T> clazz, PhysicalOperator operator) {
      super();
      this.clazz = clazz;
      this.operator = operator;
    }
  }

  protected abstract JoinInfo getJoinInfo(List<JoinCondition> conditions, JoinRelType type);

  @Test
  public void emptyRight() throws Exception {
    JoinInfo joinInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("n_nationKey"), f("key"))),
      JoinRelType.INNER);

    final Table expected = t(
      th("key", "value", "n_nationKey"),
      true,
      tr(0L, 0L, 0L) // fake row to match types.
    );

    validateDual(joinInfo.operator, joinInfo.clazz,
      TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_nationKey"),
      new EmptyGenerator(getTestAllocator()), DEFAULT_BATCH, expected, false);
  }

  @Test
  public void emptyRightWithLeftJoin() throws Exception {
    JoinInfo joinInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("r_regionKey"), f("key"))),
      JoinRelType.LEFT);

    final Table expected = t(
      th("key", "value", "r_regionKey"),
      false,
      tr(NULL_BIGINT, NULL_BIGINT, 0L),
      tr(NULL_BIGINT, NULL_BIGINT, 1L),
      tr(NULL_BIGINT, NULL_BIGINT, 2L),
      tr(NULL_BIGINT, NULL_BIGINT, 3L),
      tr(NULL_BIGINT, NULL_BIGINT, 4L)
    );


    validateDual(joinInfo.operator, joinInfo.clazz,
      TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey"),
      new EmptyGenerator(getTestAllocator()), DEFAULT_BATCH, expected);
  }

  @Test
  public void isNotDistinctWithNulls() throws Exception{
    JoinInfo includeNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("IS_NOT_DISTINCT_FROM", f("id1"), f("id2"))), JoinRelType.INNER);
    final Table includeNulls = t(
        th("id2", "name2", "id1", "name1"),
        tr(Fixtures.NULL_BIGINT, "b1", Fixtures.NULL_BIGINT, "a1"),
        tr(4l, "b2", 4l, "a2")
        );
    nullKeys(includeNullsInfo, includeNulls);

  }

  @Test
  public void noNullEquivalenceWithNulls() throws Exception{
    JoinInfo noNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.INNER);
    final Table noNulls = t(
        th("id2", "name2", "id1", "name1"),
        tr(4l, "b2", 4l, "a2")
        );
    nullKeys(noNullsInfo, noNulls);
  }

  @Test
  public void noNullEquivalenceWithNullsLeft() throws Exception{
    JoinInfo noNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.LEFT);
    final Table noNulls = t(
        th("id2", "name2", "id1", "name1"),
        tr(Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR, Fixtures.NULL_BIGINT, "a1"),
        tr(4l, "b2", 4l, "a2")
        );
    nullKeys(noNullsInfo, noNulls);
  }

  @Test
  public void noNullEquivalenceWithNullsRight() throws Exception{
    JoinInfo noNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.RIGHT);
    final Table noNulls = t(
        th("id2", "name2", "id1", "name1"),
        tr(4l, "b2", 4l, "a2"),
        tr(Fixtures.NULL_BIGINT, "b1", Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR)
        );
    nullKeys(noNullsInfo, noNulls);
  }

  private void nullKeys(JoinInfo info, Table expected) throws Exception {
    final Table left = t(
        th("id1", "name1"),
        tr(Fixtures.NULL_BIGINT, "a1"),
        tr(4l, "a2")
        );

    final Table right = t(
        th("id2", "name2"),
        tr(Fixtures.NULL_BIGINT, "b1"),
        tr(4l, "b2")
        );
    validateDual(
        info.operator, info.clazz,
        left.toGenerator(getTestAllocator()),
        right.toGenerator(getTestAllocator()),
        DEFAULT_BATCH, expected);
  }

  @Test
  public void noNullEquivalenceWithNullsLeftForString() throws Exception{
    JoinInfo noNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("name1"), f("name2"))), JoinRelType.LEFT);
    final Table noNulls = t(
      th("id2", "name2", "id1", "name1"),
      tr(4l, "a1", 1l, "a1"),
      tr(Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR, 2l, "a2"),
      tr(Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR, 3l, Fixtures.NULL_VARCHAR)
    );
    nullKeysForString(noNullsInfo, noNulls);
  }

  @Test
  public void noNullEquivalenceWithNullsRightForString() throws Exception{
    JoinInfo noNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("name1"), f("name2"))), JoinRelType.RIGHT);
    final Table noNulls = t(
      th("id2", "name2", "id1", "name1"),
      tr(4l, "a1", 1l, "a1"),
      tr(5l, "a3", Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR),
      tr(6l, Fixtures.NULL_VARCHAR, Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR)
    );
    nullKeysForString(noNullsInfo, noNulls);
  }

  private void nullKeysForString(JoinInfo info, Table expected) throws Exception {
    final Table left = t(
      th("id1", "name1"),
      tr(1l, "a1"),
      tr(2l, "a2"),
      tr(3l, Fixtures.NULL_VARCHAR)
    );

    final Table right = t(
      th("id2", "name2"),
      tr(4l, "a1"),
      tr(5l, "a3"),
      tr(6l, Fixtures.NULL_VARCHAR)
    );
    validateDual(
      info.operator, info.clazz,
      left.toGenerator(getTestAllocator()),
      right.toGenerator(getTestAllocator()),
      DEFAULT_BATCH, expected);
  }

  @Test
  public void isNotDistinctWithZeroKey() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("IS_NOT_DISTINCT_FROM", f("id1"), f("id2"))), JoinRelType.INNER);
    final Table expected = t(
      th("id2", "name2", "id1", "name1"),
      tr(Fixtures.NULL_BIGINT, "b1", Fixtures.NULL_BIGINT, "a1"),
      tr(Fixtures.NULL_BIGINT, "b3", Fixtures.NULL_BIGINT, "a1"),
      tr(0l, "b2", 0l, "a2"),
      tr(0l, "b4", 0l, "a2")
    );
    nullWithZeroKey(includeZeroKeyInfo, expected);
  }

  @Test
  public void noNullEquivalenceWithZeroKey() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.INNER);
    final Table expected = t(
      th("id2", "name2", "id1", "name1"),
      tr(0l, "b2", 0l, "a2"),
      tr(0l, "b4", 0l, "a2")
    );
    nullWithZeroKey(includeZeroKeyInfo, expected);
  }

  @Test
  public void noNullEquivalenceWithZeroKeyLeft() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.LEFT);
    final Table expected = t(
      th("id2", "name2", "id1", "name1"),
      tr(Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR, Fixtures.NULL_BIGINT, "a1"),
      tr(0l, "b2", 0l, "a2"),
      tr(0l, "b4", 0l, "a2")
    );
    nullWithZeroKey(includeZeroKeyInfo, expected);
  }

  @Test
  public void noNullEquivalenceWithZeroKeyRight() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.RIGHT);
    final Table expected = t(
      th("id2", "name2", "id1", "name1"),
      tr(0l, "b2", 0l, "a2"),
      tr(0l, "b4", 0l, "a2"),
      tr(Fixtures.NULL_BIGINT, "b1", Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR),
      tr(Fixtures.NULL_BIGINT, "b3", Fixtures.NULL_BIGINT, Fixtures.NULL_VARCHAR)
    );
    nullWithZeroKey(includeZeroKeyInfo, expected);
  }

  private void nullWithZeroKey(JoinInfo info, Table expected) throws Exception {
    final Table left = t(
      th("id1", "name1"),
      tr(Fixtures.NULL_BIGINT, "a1"),
      tr(0l, "a2")
    );

    final Table right = t(
      th("id2", "name2"),
      tr(Fixtures.NULL_BIGINT, "b1"),
      tr(0l, "b2"),
      tr(Fixtures.NULL_BIGINT, "b3"),
      tr(0l, "b4")
    );
    validateDual(
      info.operator, info.clazz,
      left.toGenerator(getTestAllocator()),
      right.toGenerator(getTestAllocator()),
      DEFAULT_BATCH, expected);
  }

  @Test
  public void freeValueInHashTable() throws Exception{
    long freeValue = Long.MIN_VALUE + 474747l;
    JoinInfo freeValueInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("id1"), f("id2"))), JoinRelType.INNER);
    final Table expected = t(
      th("id2", "name2", "id1", "name1"),
      tr(0l, "b2", 0l, "a2"),
      tr(freeValue, "b3", freeValue, "a3")
    );

    final Table left = t(
      th("id1", "name1"),
      tr(Fixtures.NULL_BIGINT, "a1"),
      tr(0l, "a2"),
      tr(freeValue, "a3")
    );

    final Table right = t(
      th("id2", "name2"),
      tr(Fixtures.NULL_BIGINT, "b1"),
      tr(0l, "b2"),
      tr(freeValue, "b3"),
      tr(4l, "b4")
    );
    validateDual(
      freeValueInfo.operator, freeValueInfo.clazz,
      left.toGenerator(getTestAllocator()),
      right.toGenerator(getTestAllocator()),
      DEFAULT_BATCH, expected);
  }

  @Test
  public void isNotDistinctBitKeys() throws Exception{
    JoinInfo includeNullsInfo = getJoinInfo(Arrays.asList(new JoinCondition("IS_NOT_DISTINCT_FROM", f("bool1"), f("bool2"))), JoinRelType.INNER);
    final Table expected = t(
      th("bool2", "name2", "bool1", "name1"),
      tr(true, "b1", true, "a1"),
      tr(false, "b2", false, "a2"),
      tr(Fixtures.NULL_BOOLEAN, "b3", Fixtures.NULL_BOOLEAN, "a3")
    );

    nullBitKeys(includeNullsInfo, expected);
  }

  @Test
  public void noNullEquivalenceBitKeys() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("bool1"), f("bool2"))), JoinRelType.INNER);
    final Table expected = t(
      th("bool2", "name2", "bool1", "name1"),
      tr(true, "b1", true, "a1"),
      tr(false, "b2", false, "a2")
    );
    nullBitKeys(includeZeroKeyInfo, expected);
  }

  @Test
  public void noNullEquivalenceBitKeysLeft() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("bool1"), f("bool2"))), JoinRelType.LEFT);
    final Table expected = t(
      th("bool2", "name2", "bool1", "name1"),
      tr(true, "b1", true, "a1"),
      tr(false, "b2", false, "a2"),
      tr(Fixtures.NULL_BOOLEAN, Fixtures.NULL_VARCHAR, Fixtures.NULL_BOOLEAN, "a3")
    );
    nullBitKeys(includeZeroKeyInfo, expected);
  }

  @Test
  public void noNullEquivalenceBitKeysRight() throws Exception{
    JoinInfo includeZeroKeyInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("bool1"), f("bool2"))), JoinRelType.RIGHT);
    final Table expected = t(
      th("bool2", "name2", "bool1", "name1"),
      tr(true, "b1", true, "a1"),
      tr(false, "b2", false, "a2"),
      tr(Fixtures.NULL_BOOLEAN, "b3", Fixtures.NULL_BOOLEAN, Fixtures.NULL_VARCHAR)
    );
    nullBitKeys(includeZeroKeyInfo, expected);
  }

  private void nullBitKeys(JoinInfo info, Table expected) throws Exception {
    final Table left = t(
      th("bool1", "name1"),
      tr(true, "a1"),
      tr(false, "a2"),
      tr(Fixtures.NULL_BOOLEAN, "a3")
    );

    final Table right = t(
      th("bool2", "name2"),
      tr(true, "b1"),
      tr(false, "b2"),
      tr(Fixtures.NULL_BOOLEAN, "b3")
    );
    validateDual(
      info.operator, info.clazz,
      left.toGenerator(getTestAllocator()),
      right.toGenerator(getTestAllocator()),
      DEFAULT_BATCH, expected);
  }

  @Test
  public void regionNationInner() throws Exception {
    JoinInfo info = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("r_regionKey"), f("n_regionKey"))), JoinRelType.INNER);

    final Table expected = t(
        th("n_name", "n_regionKey", "r_regionKey", "r_name"),
        tr("ALGERIA", 0L, 0L, "AFRICA"),
        tr("MOZAMBIQUE", 0L, 0L, "AFRICA"),
        tr("MOROCCO", 0L, 0L, "AFRICA"),
        tr("KENYA", 0L, 0L, "AFRICA"),
        tr("ETHIOPIA", 0L, 0L, "AFRICA"),
        tr("ARGENTINA", 1L, 1L, "AMERICA"),
        tr("UNITED STATES", 1L, 1L, "AMERICA"),
        tr("PERU", 1L, 1L, "AMERICA"),
        tr("CANADA", 1L, 1L, "AMERICA"),
        tr("BRAZIL", 1L, 1L, "AMERICA"),
        tr("INDIA", 2L, 2L, "ASIA"),
        tr("VIETNAM", 2L, 2L, "ASIA"),
        tr("CHINA", 2L, 2L, "ASIA"),
        tr("JAPAN", 2L, 2L, "ASIA"),
        tr("INDONESIA", 2L, 2L, "ASIA"),
        tr("FRANCE", 3L, 3L, "EUROPE"),
        tr("UNITED KINGDOM", 3L, 3L, "EUROPE"),
        tr("RUSSIA", 3L, 3L, "EUROPE"),
        tr("ROMANIA", 3L, 3L, "EUROPE"),
        tr("GERMANY", 3L, 3L, "EUROPE"),
        tr("EGYPT", 4L, 4L, "MIDDLE EAST"),
        tr("SAUDI ARABIA", 4L, 4L, "MIDDLE EAST"),
        tr("JORDAN", 4L, 4L, "MIDDLE EAST"),
        tr("IRAQ", 4L, 4L, "MIDDLE EAST"),
        tr("IRAN", 4L, 4L, "MIDDLE EAST")
        );

    validateDual(
        info.operator, info.clazz,
        TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey", "r_name"),
        TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_regionKey", "n_name"),
        DEFAULT_BATCH, expected);
  }

  @Test
  public void nationRegionPartialKeyRight() throws Exception {
    JoinInfo info = getJoinInfo( Arrays.asList(new JoinCondition("EQUALS", f("n_nationKey"), f("r_regionKey"))), JoinRelType.RIGHT);

    final Table expected = t(
        th("r_regionKey", "r_name", "n_nationKey", "n_name"),
        tr(0L, "AFRICA", 0L, "ALGERIA"),
        tr(1L, "AMERICA", 1L, "ARGENTINA"),
        tr(2L, "ASIA", 2L, "BRAZIL"),
        tr(3L, "EUROPE", 3L, "CANADA"),
        tr(4L, "MIDDLE EAST", 4L, "EGYPT")
        );

    validateDual(
        info.operator, info.clazz,
        TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_nationKey", "n_name"),
        TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey", "r_name"),
        DEFAULT_BATCH, expected);
  }

  @Test
  public void regionNationPartialKeyRight() throws Exception {

    JoinInfo info = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("r_regionKey"), f("n_nationKey"))), JoinRelType.RIGHT);

    final Table expected = t(
        th("n_nationKey", "n_name", "r_regionKey", "r_name"),
        tr(0L, "ALGERIA", 0L, "AFRICA"),
        tr(1L, "ARGENTINA", 1L, "AMERICA"),
        tr(2L, "BRAZIL", 2L, "ASIA"),
        tr(3L, "CANADA", 3L, "EUROPE"),
        tr(4L, "EGYPT", 4L, "MIDDLE EAST"),
        tr(5L, "ETHIOPIA", NULL_BIGINT, NULL_VARCHAR),
        tr(6L, "FRANCE", NULL_BIGINT, NULL_VARCHAR),
        tr(7L, "GERMANY", NULL_BIGINT, NULL_VARCHAR),
        tr(8L, "INDIA", NULL_BIGINT, NULL_VARCHAR),
        tr(9L, "INDONESIA", NULL_BIGINT, NULL_VARCHAR),
        tr(10L, "IRAN", NULL_BIGINT, NULL_VARCHAR),
        tr(11L, "IRAQ", NULL_BIGINT, NULL_VARCHAR),
        tr(12L, "JAPAN", NULL_BIGINT, NULL_VARCHAR),
        tr(13L, "JORDAN", NULL_BIGINT, NULL_VARCHAR),
        tr(14L, "KENYA", NULL_BIGINT, NULL_VARCHAR),
        tr(15L, "MOROCCO", NULL_BIGINT, NULL_VARCHAR),
        tr(16L, "MOZAMBIQUE", NULL_BIGINT, NULL_VARCHAR),
        tr(17L, "PERU", NULL_BIGINT, NULL_VARCHAR),
        tr(18L, "CHINA", NULL_BIGINT, NULL_VARCHAR),
        tr(19L, "ROMANIA", NULL_BIGINT, NULL_VARCHAR),
        tr(20L, "SAUDI ARABIA", NULL_BIGINT, NULL_VARCHAR),
        tr(21L, "VIETNAM", NULL_BIGINT, NULL_VARCHAR),
        tr(22L, "RUSSIA", NULL_BIGINT, NULL_VARCHAR),
        tr(23L, "UNITED KINGDOM", NULL_BIGINT, NULL_VARCHAR),
        tr(24L, "UNITED STATES", NULL_BIGINT, NULL_VARCHAR)
        );

    validateDual(
        info.operator, info.clazz,
        TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey", "r_name"),
        TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_nationKey", "n_name"),
        DEFAULT_BATCH, expected);
  }

  @Test
  public void nationRegionPartialKeyLeft() throws Exception {

    JoinInfo info = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("n_nationKey"), f("r_regionKey"))), JoinRelType.LEFT);

    final Table expected = t(
        th("r_regionKey", "r_name", "n_nationKey", "n_name"),
        tr(0L, "AFRICA", 0L, "ALGERIA"),
        tr(1L, "AMERICA", 1L, "ARGENTINA"),
        tr(2L, "ASIA", 2L, "BRAZIL"),
        tr(3L, "EUROPE", 3L, "CANADA"),
        tr(4L, "MIDDLE EAST", 4L, "EGYPT"),
        tr(NULL_BIGINT, NULL_VARCHAR, 5L, "ETHIOPIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 6L, "FRANCE"),
        tr(NULL_BIGINT, NULL_VARCHAR, 7L, "GERMANY"),
        tr(NULL_BIGINT, NULL_VARCHAR, 8L, "INDIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 9L, "INDONESIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 10L, "IRAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 11L, "IRAQ"),
        tr(NULL_BIGINT, NULL_VARCHAR, 12L, "JAPAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 13L, "JORDAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 14L, "KENYA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 15L, "MOROCCO"),
        tr(NULL_BIGINT, NULL_VARCHAR, 16L, "MOZAMBIQUE"),
        tr(NULL_BIGINT, NULL_VARCHAR, 17L, "PERU"),
        tr(NULL_BIGINT, NULL_VARCHAR, 18L, "CHINA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 19L, "ROMANIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 20L, "SAUDI ARABIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 21L, "VIETNAM"),
        tr(NULL_BIGINT, NULL_VARCHAR, 22L, "RUSSIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 23L, "UNITED KINGDOM"),
        tr(NULL_BIGINT, NULL_VARCHAR, 24L, "UNITED STATES")
        );

    validateDual(
        info.operator, info.clazz,
        TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_nationKey", "n_name"),
        TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey", "r_name"),
        DEFAULT_BATCH, expected);
  }

  @Test
  public void nationRegionPartialKeyOuter() throws Exception {
    JoinInfo info = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("n_nationKey"), f("r_regionKey"))), JoinRelType.FULL);

    final Table expected = t(
        th("r_regionKey", "r_name", "n_nationKey", "n_name"),
        tr(0L, "AFRICA", 0L, "ALGERIA"),
        tr(1L, "AMERICA", 1L, "ARGENTINA"),
        tr(2L, "ASIA", 2L, "BRAZIL"),
        tr(3L, "EUROPE", 3L, "CANADA"),
        tr(4L, "MIDDLE EAST", 4L, "EGYPT"),
        tr(NULL_BIGINT, NULL_VARCHAR, 5L, "ETHIOPIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 6L, "FRANCE"),
        tr(NULL_BIGINT, NULL_VARCHAR, 7L, "GERMANY"),
        tr(NULL_BIGINT, NULL_VARCHAR, 8L, "INDIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 9L, "INDONESIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 10L, "IRAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 11L, "IRAQ"),
        tr(NULL_BIGINT, NULL_VARCHAR, 12L, "JAPAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 13L, "JORDAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 14L, "KENYA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 15L, "MOROCCO"),
        tr(NULL_BIGINT, NULL_VARCHAR, 16L, "MOZAMBIQUE"),
        tr(NULL_BIGINT, NULL_VARCHAR, 17L, "PERU"),
        tr(NULL_BIGINT, NULL_VARCHAR, 18L, "CHINA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 19L, "ROMANIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 20L, "SAUDI ARABIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 21L, "VIETNAM"),
        tr(NULL_BIGINT, NULL_VARCHAR, 22L, "RUSSIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 23L, "UNITED KINGDOM"),
        tr(NULL_BIGINT, NULL_VARCHAR, 24L, "UNITED STATES")
        );

    validateDual(
        info.operator, info.clazz,
        TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_nationKey", "n_name"),
        TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey", "r_name"),
        DEFAULT_BATCH, expected);
  }

  @Test
  public void nationRegionPartialKeyOuterSmall() throws Exception {
    JoinInfo info = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("n_nationKey"), f("r_regionKey"))), JoinRelType.FULL);

    final Table expected = t(
        th("r_regionKey", "r_name", "n_nationKey", "n_name"),
        tr(0L, "AFRICA", 0L, "ALGERIA"),
        tr(1L, "AMERICA", 1L, "ARGENTINA"),
        tr(2L, "ASIA", 2L, "BRAZIL"),
        tr(3L, "EUROPE", 3L, "CANADA"),
        tr(4L, "MIDDLE EAST", 4L, "EGYPT"),
        tr(NULL_BIGINT, NULL_VARCHAR, 5L, "ETHIOPIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 6L, "FRANCE"),
        tr(NULL_BIGINT, NULL_VARCHAR, 7L, "GERMANY"),
        tr(NULL_BIGINT, NULL_VARCHAR, 8L, "INDIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 9L, "INDONESIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 10L, "IRAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 11L, "IRAQ"),
        tr(NULL_BIGINT, NULL_VARCHAR, 12L, "JAPAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 13L, "JORDAN"),
        tr(NULL_BIGINT, NULL_VARCHAR, 14L, "KENYA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 15L, "MOROCCO"),
        tr(NULL_BIGINT, NULL_VARCHAR, 16L, "MOZAMBIQUE"),
        tr(NULL_BIGINT, NULL_VARCHAR, 17L, "PERU"),
        tr(NULL_BIGINT, NULL_VARCHAR, 18L, "CHINA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 19L, "ROMANIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 20L, "SAUDI ARABIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 21L, "VIETNAM"),
        tr(NULL_BIGINT, NULL_VARCHAR, 22L, "RUSSIA"),
        tr(NULL_BIGINT, NULL_VARCHAR, 23L, "UNITED KINGDOM"),
        tr(NULL_BIGINT, NULL_VARCHAR, 24L, "UNITED STATES")
        );

    validateDual(
        info.operator, info.clazz,
        TpchGenerator.singleGenerator(TpchTable.NATION, 0.1, getTestAllocator(), "n_nationKey", "n_name"),
        TpchGenerator.singleGenerator(TpchTable.REGION, 0.1, getTestAllocator(), "r_regionKey", "r_name"),
        3, expected);
  }

  @Test
  public void manyKeys() throws Exception {
    JoinInfo info = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("col1_1"), f("col2_1")),
      new JoinCondition("EQUALS", f("col1_2"), f("col2_2")),
      new JoinCondition("EQUALS", f("col1_3"), f("col2_3")),
      new JoinCondition("EQUALS", f("col1_4"), f("col2_4")),
      new JoinCondition("EQUALS", f("col1_5"), f("col2_5")),
      new JoinCondition("EQUALS", f("col1_6"), f("col2_6")),
      new JoinCondition("EQUALS", f("col1_7"), f("col2_7")),
      new JoinCondition("EQUALS", f("col1_8"), f("col2_8")),
      new JoinCondition("EQUALS", f("col1_9"), f("col2_9")),
      new JoinCondition("EQUALS", f("col1_10"), f("col2_10")),
      new JoinCondition("EQUALS", f("col1_11"), f("col2_11")),
      new JoinCondition("EQUALS", f("col1_12"), f("col2_12")),
      new JoinCondition("EQUALS", f("col1_13"), f("col2_13")),
      new JoinCondition("EQUALS", f("col1_14"), f("col2_14")),
      new JoinCondition("EQUALS", f("col1_15"), f("col2_15")),
      new JoinCondition("EQUALS", f("col1_16"), f("col2_16")),
      new JoinCondition("EQUALS", f("col1_17"), f("col2_17")),
      new JoinCondition("EQUALS", f("col1_18"), f("col2_18")),
      new JoinCondition("EQUALS", f("col1_19"), f("col2_19")),
      new JoinCondition("EQUALS", f("col1_20"), f("col2_20")),
      new JoinCondition("EQUALS", f("col1_21"), f("col2_21")),
      new JoinCondition("EQUALS", f("col1_22"), f("col2_22")),
      new JoinCondition("EQUALS", f("col1_23"), f("col2_23")),
      new JoinCondition("EQUALS", f("col1_24"), f("col2_24")),
      new JoinCondition("EQUALS", f("col1_25"), f("col2_25")),
      new JoinCondition("EQUALS", f("col1_26"), f("col2_26")),
      new JoinCondition("EQUALS", f("col1_27"), f("col2_27")),
      new JoinCondition("EQUALS", f("col1_28"), f("col2_28")),
      new JoinCondition("EQUALS", f("col1_29"), f("col2_29")),
      new JoinCondition("EQUALS", f("col1_30"), f("col2_30")),
      new JoinCondition("EQUALS", f("col1_30"), f("col2_30")),
      new JoinCondition("EQUALS", f("col1_31"), f("col2_31")),
      new JoinCondition("EQUALS", f("col1_32"), f("col2_32")),
      new JoinCondition("EQUALS", f("col1_33"), f("col2_33"))
      ), JoinRelType.INNER);

    final Table expected = t(
      th("col2_1", "col2_2", "col2_3", "col2_4", "col2_5", "col2_6", "col2_7", "col2_8", "col2_9", "col2_10",
        "col2_11", "col2_12", "col2_13", "col2_14", "col2_15", "col2_16", "col2_17", "col2_18", "col2_19", "col2_20",
        "col2_21", "col2_22", "col2_23", "col2_24", "col2_25", "col2_26", "col2_27", "col2_28", "col2_29", "col2_30",
        "col2_31", "col2_32", "col2_33", "col2_34",
        "col1_1", "col1_2", "col1_3", "col1_4", "col1_5", "col1_6", "col1_7", "col1_8", "col1_9", "col1_10",
        "col1_11", "col1_12", "col1_13", "col1_14", "col1_15", "col1_16", "col1_17", "col1_18", "col1_19", "col1_20",
        "col1_21", "col1_22", "col1_23", "col1_24", "col1_25", "col1_26", "col1_27", "col1_28", "col1_29", "col1_30",
        "col1_31", "col1_32", "col1_33", "col1_34"

        ),
      tr(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 34l,
        1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 34l
      ),
      tr(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 100l,
        1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 34l
      )
    );

    final Table left = t(
      th("col1_1", "col1_2", "col1_3", "col1_4", "col1_5", "col1_6", "col1_7", "col1_8", "col1_9", "col1_10",
        "col1_11", "col1_12", "col1_13", "col1_14", "col1_15", "col1_16", "col1_17", "col1_18", "col1_19", "col1_20",
        "col1_21", "col1_22", "col1_23", "col1_24", "col1_25", "col1_26", "col1_27", "col1_28", "col1_29", "col1_30",
        "col1_31", "col1_32", "col1_33", "col1_34"
        ),
      tr(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 34l
      )
    );

    final Table right = t(
      th("col2_1", "col2_2", "col2_3", "col2_4", "col2_5", "col2_6", "col2_7", "col2_8", "col2_9", "col2_10",
        "col2_11", "col2_12", "col2_13", "col2_14", "col2_15", "col2_16", "col2_17", "col2_18", "col2_19", "col2_20",
        "col2_21", "col2_22", "col2_23", "col2_24", "col2_25", "col2_26", "col2_27", "col2_28", "col2_29", "col2_30",
        "col2_31", "col2_32", "col2_33", "col2_34"
      ),
      tr(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 34l
      ),
      tr(1l, 2l, 3l, 4l, 5l, 6l, 7l, 8l, 9l, 10l,
        11l, 12l, 13l, 14l, 15l, 16l, 17l, 18l, 19l, 20l,
        21l, 22l, 23l, 24l, 25l, 26l, 27l, 28l, 29l, 30l,
        31l, 32l, 33l, 100l
      )
    );
    validateDual(
      info.operator, info.clazz,
      left.toGenerator(getTestAllocator()),
      right.toGenerator(getTestAllocator()),
      DEFAULT_BATCH, expected);
  }

  private static final class ManyColumnsGenerator implements Generator {
    private final int columns;
    private final int rows;
    private final VectorContainer result;
    private final List<IntVector> vectors;

    private int offset = 0;

    public ManyColumnsGenerator(BufferAllocator allocator, String prefix, int columns, int rows) {
      this.columns = columns;
      this.rows = rows;
      result = new VectorContainer(allocator);

      ImmutableList.Builder<IntVector> vectorsBuilder = ImmutableList.builder();
      for(int i = 0; i<columns; i++) {
        IntVector vector = result.addOrGet(String.format("%s_%d", prefix, i + 1), Types.optional(MinorType.INT), IntVector.class);
        vectorsBuilder.add(vector);
      }
      this.vectors = vectorsBuilder.build();

      result.buildSchema(SelectionVectorMode.NONE);
    }

    @Override
    public int next(int records) {
      int count = Math.min(rows - offset, records);
      if (count == 0) {
        return 0;
      }

      result.allocateNew();
      for(int i = 0; i<count; i++) {
        int col = 0;
        for(IntVector vector: vectors) {
          vector.setSafe(i, (offset + i) * columns + col);
          col++;
        }
      }

      offset += count;
      result.setAllCount(count);

      return count;
    }

    @Override
    public VectorAccessible getOutput() {
      return result;
    }

    @Override
    public void close() throws Exception {
      result.close();
    }
  }

  private static HeaderRow getHeader(String leftPrefix, int leftColumns, String rightPrefix, int rightColumns) {
    String[] names = new String[leftColumns + rightColumns];
    for(int i = 0; i<leftColumns; i++) {
      names[i] = String.format("%s_%d", leftPrefix, i+1);
    }
    for(int i = 0; i<rightColumns; i++) {
      names[i + leftColumns] = String.format("%s_%d", rightPrefix, i+1);
    }
    return new HeaderRow(names);
  }

  private static DataRow[] getData( int leftColumns, int rightColumns, int count) {
    DataRow[] rows = new DataRow[count];

    for(int i = 0; i<count; i++) {
      Object[] objects = new Object[leftColumns + rightColumns];

      for(int j = 0; j<leftColumns; j++) {
        objects[j] = i * leftColumns + j;
      }
      for(int j = 0; j<rightColumns; j++) {
        objects[j + leftColumns] = i * rightColumns + j;
      }
      rows[i] = tr(objects);
    }

    return rows;
  }

  protected void baseManyColumns() throws Exception {
    int columns = 1000;
    int leftColumns = columns;
    int rightColumns = columns;

    JoinInfo joinInfo = getJoinInfo(Arrays.asList(new JoinCondition("EQUALS", f("left_1"), f("right_1"))), JoinRelType.LEFT);

    Table expected = t(getHeader("right", rightColumns, "left", leftColumns), false, getData(columns, leftColumns, 1));
    validateDual(joinInfo.operator, joinInfo.clazz,
        new ManyColumnsGenerator(getTestAllocator(), "left", leftColumns, 1),
        new ManyColumnsGenerator(getTestAllocator(), "right", rightColumns, 1),
        DEFAULT_BATCH, expected);
  }
}
