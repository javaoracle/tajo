/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tajo.plan.rewrite;

import org.apache.tajo.OverridableConf;
import org.apache.tajo.exception.TajoException;
import org.apache.tajo.plan.LogicalPlan;

public interface LogicalPlanRewriteEngine {
  /**
   * Rewrite a logical plan with all query rewrite rules added to this engine.
   *
   * @param plan The plan to be rewritten with all query rewrite rule.
   * @return The rewritten plan.
   */
  LogicalPlan rewrite(OverridableConf queryContext, LogicalPlan plan) throws TajoException;
}
