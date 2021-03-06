/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tajo.exception;

import org.apache.tajo.error.Errors.ResultCode;
import org.apache.tajo.rpc.protocolrecords.PrimitiveProtos.ReturnState;

/**
 * It is used in unexpected cases or error that we know the cause.
 *
 * @see @{link TajoException}
 */
public class TajoRuntimeException extends RuntimeException implements TajoExceptionInterface {
  private ResultCode code;

  public TajoRuntimeException(ReturnState state) {
    super(state.getMessage());
    this.code = state.getReturnCode();
  }

  public TajoRuntimeException(ResultCode code) {
    super(ErrorMessages.getMessage(code));
    this.code = code;
  }

  public TajoRuntimeException(ResultCode code, String ... args) {
    super(ErrorMessages.getMessage(code, args));
    this.code = code;
  }

  public TajoRuntimeException(TajoException e) {
    super(e.getMessage());
    this.code = e.getErrorCode();
  }

  @Override
  public ResultCode getErrorCode() {
    return code;
  }
}
