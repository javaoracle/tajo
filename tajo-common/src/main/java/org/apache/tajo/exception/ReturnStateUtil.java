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

import com.google.common.base.Preconditions;
import org.apache.tajo.QueryId;
import org.apache.tajo.common.TajoDataTypes;
import org.apache.tajo.common.TajoDataTypes.DataType;
import org.apache.tajo.error.Errors.ResultCode;
import org.apache.tajo.exception.ErrorMessages;
import org.apache.tajo.exception.ErrorUtil;
import org.apache.tajo.exception.ExceptionUtil;
import org.apache.tajo.exception.TajoExceptionInterface;
import org.apache.tajo.rpc.protocolrecords.PrimitiveProtos;
import org.apache.tajo.rpc.protocolrecords.PrimitiveProtos.ReturnState;
import org.apache.tajo.rpc.protocolrecords.PrimitiveProtos.StringListResponse;

import java.util.Collection;

public class ReturnStateUtil {

  public static final ReturnState OK;

  static {
    ReturnState.Builder builder = ReturnState.newBuilder();
    builder.setReturnCode(ResultCode.OK);
    OK = builder.build();
  }

  public static void ensureOk(ReturnState state) {
    if (isError(state)) {
      throw new TajoRuntimeException(state);
    }
  }

  public static StringListResponse returnStringList(Collection<String> values) {
    return StringListResponse.newBuilder()
        .setState(OK)
        .addAllValues(values)
        .build();
  }

  public static StringListResponse returnFailedStringList(Throwable t) {
    return StringListResponse.newBuilder()
        .setState(returnError(t))
        .build();
  }

  public static ReturnState returnError(ResultCode code) {
    ReturnState.Builder builder = ReturnState.newBuilder();
    builder.setReturnCode(code);
    builder.setMessage(ErrorMessages.getMessage(code));
    return builder.build();
  }

  public static ReturnState returnError(ResultCode code, String...args) {
    Preconditions.checkNotNull(args);

    ReturnState.Builder builder = ReturnState.newBuilder();
    builder.setReturnCode(code);
    builder.setMessage(ErrorMessages.getMessage(code, args));
    return builder.build();
  }

  public static ReturnState returnError(Throwable t) {
    ReturnState.Builder builder = ReturnState.newBuilder();

    if (ExceptionUtil.isExceptionWithResultCode(t)) {
      TajoExceptionInterface tajoException = (TajoExceptionInterface) t;
      builder.setReturnCode(tajoException.getErrorCode());
      builder.setMessage(tajoException.getMessage());
    } else {
      builder.setReturnCode(ResultCode.INTERNAL_ERROR);
      builder.setMessage(ErrorMessages.getInternalErrorMessage(t));
      builder.setStackTrace(ErrorUtil.convertStacktrace(t));
    }

    return builder.build();
  }

  /**
   * This method check if the state is successful.
   *
   * @param state ResponseState to be checked
   * @return True if ResponseState is success.
   */
  public static boolean isSuccess(ReturnState state) {
    return ErrorUtil.isOk(state.getReturnCode());
  }

  /**
   * This method check if the state is failed.
   *
   * @param state ResponseState to be checked
   * @return True if ResponseState is failed.
   */
  public static boolean isError(ReturnState state) {
    return ErrorUtil.isFailed(state.getReturnCode());
  }

  public static boolean isThisError(ReturnState state, ResultCode code) {
    return state.getReturnCode() == code;
  }

  public static ReturnState errFeatureNotSupported(String feature) {
    return returnError(ResultCode.FEATURE_NOT_SUPPORTED, feature);
  }

  public static ReturnState errInvalidRpcCall(String message) {
    return returnError(ResultCode.INVALID_RPC_CALL, message);
  }

  public static ReturnState errNoSuchQueryId(QueryId queryId) {
    return returnError(ResultCode.NO_SUCH_QUERYID, queryId.toString());
  }

  public static ReturnState errNoData(QueryId queryId) {
    return returnError(ResultCode.NO_DATA, queryId.toString());
  }

  public static ReturnState errIncompleteQuery(QueryId queryId) {
    return returnError(ResultCode.INCOMPLETE_QUERY, queryId.toString());
  }

  public static ReturnState errInvalidSession(String sessionId) {
    return returnError(ResultCode.INVALID_SESSION, sessionId);
  }

  public static ReturnState errNoSessionVar(String varName) {
    return returnError(ResultCode.NO_SUCH_QUERYID, varName);
  }

  public static ReturnState errInsufficientPrivilege(String message) {
    return returnError(ResultCode.INSUFFICIENT_PRIVILEGE, message);
  }

  public static ReturnState errUndefinedTablespace(String spaceName) {
    return returnError(ResultCode.UNDEFINED_TABLESPACE, spaceName);
  }

  public static ReturnState errUndefinedDatabase(String dbName) {
    return returnError(ResultCode.UNDEFINED_DATABASE, dbName);
  }

  public static ReturnState errUndefinedTable(String tbName) {
    return returnError(ResultCode.UNDEFINED_TABLE, tbName);
  }

  public static ReturnState errUndefinedPartition(String partitionName) {
    return returnError(ResultCode.UNDEFINED_PARTITION, partitionName);
  }

  public static ReturnState errUndefinedPartitionMethod(String tbName) {
    return returnError(ResultCode.UNDEFINED_PARTITION_METHOD, tbName);
  }

  public static ReturnState errUndefinedIndex(String tbName, String columnName) {
    return returnError(ResultCode.UNDEFINED_INDEX, tbName, columnName);
  }

  public static ReturnState errUndefinedIndexName(String indexName) {
    return returnError(ResultCode.UNDEFINED_INDEX_NAME, indexName);
  }

  public static ReturnState errUndefinedFunction(String funcName) {
    return returnError(ResultCode.UNDEFINED_FUNCTION, funcName);
  }

  public static ReturnState errDuplicateDatabase(String dbName) {
    return returnError(ResultCode.DUPLICATE_DATABASE, dbName);
  }

  public static ReturnState errDuplicateTable(String tbName) {
    return returnError(ResultCode.DUPLICATE_TABLE, tbName);
  }

  public static ReturnState errDuplicateIndex(String indexName) {
    return returnError(ResultCode.DUPLICATE_INDEX, indexName);
  }

  public static ReturnState errDuplicateFunction(String signature) {
    return returnError(ResultCode.DUPLICATE_FUNCTION, signature);
  }
}
