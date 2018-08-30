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
package com.dremio.sabot.exec.context;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;

import org.apache.arrow.memory.BufferAllocator;
import org.apache.arrow.vector.types.pojo.Schema;

import com.dremio.common.config.SabotConfig;
import com.dremio.exec.expr.ClassProducer;
import com.dremio.exec.physical.base.PhysicalOperator;
import com.dremio.exec.proto.ExecProtos.FragmentHandle;
import com.dremio.exec.record.VectorContainer;
import com.dremio.exec.record.selection.SelectionVector2;
import com.dremio.exec.server.NodeDebugContextProvider;
import com.dremio.options.OptionManager;
import com.dremio.exec.testing.ExecutionControls;
import com.dremio.sabot.op.filter.VectorContainerWithSV;
import com.dremio.service.namespace.NamespaceService;

import io.netty.buffer.ArrowBuf;

public abstract class OperatorContext {

  public abstract SabotConfig getConfig();

  public abstract ArrowBuf replace(ArrowBuf old, int newSize);

  public abstract ArrowBuf getManagedBuffer();

  public abstract ArrowBuf getManagedBuffer(int size);

  public abstract BufferAllocator getAllocator();

  /**
   * Create a vector container to be used for the output of this operator
   * Allocations for this vector container come from a special fragment output allocator
   */
  public abstract VectorContainer createOutputVectorContainer();

  /**
   * Create a vector container to be used for the output of this operator
   * Allocations for this vector container come from a special fragment output allocator
   */
  public abstract VectorContainer createOutputVectorContainer(Schema schema);

  /**
   * Create a vector container with selection vector, to be used for the output of this operator
   * Allocations for this vector container come from a special fragment output allocator
   */
  public abstract VectorContainerWithSV createOutputVectorContainerWithSV();

  /**
   * Create a vector container with selection vector cloned from the incoming selection vector, to be used for the
   * output of this operator
   * Allocations for this vector container come from a special fragment output allocator
   */
  public abstract VectorContainerWithSV createOutputVectorContainerWithSV(SelectionVector2 incomingSv);

  public abstract OperatorStats getStats();

  public abstract ExecutionControls getExecutionControls();

  public abstract OptionManager getOptions();

  public abstract int getTargetBatchSize();

  public abstract ClassProducer getClassProducer();

  public abstract FunctionContext getFunctionContext();

  public abstract FragmentHandle getFragmentHandle();

  public abstract ExecutorService getExecutor();

  public abstract NamespaceService getNamespaceService();

  public abstract NodeDebugContextProvider getNodeDebugContextProvider();

  public static int getChildCount(PhysicalOperator popConfig) {
    Iterator<PhysicalOperator> iter = popConfig.iterator();
    int i = 0;
    while (iter.hasNext()) {
      iter.next();
      i++;
    }

    if (i == 0) {
      i = 1;
    }
    return i;
  }

  public interface Creator {
    public OperatorContext newOperatorContext(PhysicalOperator popConfig) throws Exception;
  }
}
