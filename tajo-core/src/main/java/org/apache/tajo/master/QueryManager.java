/**
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

package org.apache.tajo.master;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections.map.LRUMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.service.CompositeService;
import org.apache.hadoop.yarn.event.AsyncDispatcher;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.tajo.QueryId;
import org.apache.tajo.QueryIdFactory;
import org.apache.tajo.ResourceProtos;
import org.apache.tajo.ResourceProtos.AllocationResourceProto;
import org.apache.tajo.ResourceProtos.TajoHeartbeatRequest;
import org.apache.tajo.ResourceProtos.TajoHeartbeatResponse;
import org.apache.tajo.TajoProtos;
import org.apache.tajo.catalog.TableDesc;
import org.apache.tajo.engine.query.QueryContext;
import org.apache.tajo.master.cluster.WorkerConnectionInfo;
import org.apache.tajo.master.scheduler.QuerySchedulingInfo;
import org.apache.tajo.plan.logical.LogicalRootNode;
import org.apache.tajo.querymaster.QueryJobEvent;
import org.apache.tajo.session.Session;
import org.apache.tajo.util.history.HistoryReader;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * QueryManager manages all scheduled and running queries.
 * It receives all Query related events and routes them to each QueryInProgress.
 */
public class QueryManager extends CompositeService {
  private static final Log LOG = LogFactory.getLog(QueryManager.class.getName());
  private static final String EMPTY_QM_HOSTNAME = "";

  // TajoMaster Context
  private final TajoMaster.MasterContext masterContext;

  private AsyncDispatcher dispatcher;

  private final Map<QueryId, QueryInProgress> submittedQueries = Maps.newConcurrentMap();

  private final Map<QueryId, QueryInProgress> runningQueries = Maps.newConcurrentMap();
  private final LRUMap historyCache = new LRUMap(HistoryReader.DEFAULT_PAGE_SIZE);

  private AtomicLong minExecutionTime = new AtomicLong(Long.MAX_VALUE);
  private AtomicLong maxExecutionTime = new AtomicLong();
  private AtomicLong avgExecutionTime = new AtomicLong();
  private AtomicLong executedQuerySize = new AtomicLong();

  public QueryManager(final TajoMaster.MasterContext masterContext) {
    super(QueryManager.class.getName());
    this.masterContext = masterContext;
  }

  @Override
  public void serviceInit(Configuration conf) throws Exception {
    try {
      this.dispatcher = new AsyncDispatcher();
      addService(this.dispatcher);

      this.dispatcher.register(QueryJobEvent.Type.class, new QueryJobManagerEventHandler());

    } catch (Exception e) {
      LOG.error("Failed to init service " + getName() + " by exception " + e, e);
    }

    super.serviceInit(conf);
  }

  @Override
  public void serviceStop() throws Exception {
    for(QueryInProgress eachQueryInProgress: runningQueries.values()) {
      eachQueryInProgress.stopProgress();
    }

    super.serviceStop();
  }

  @Override
  public void serviceStart() throws Exception {
    super.serviceStart();
  }

  public EventHandler getEventHandler() {
    return dispatcher.getEventHandler();
  }

  public Collection<QueryInProgress> getSubmittedQueries() {
    return Collections.unmodifiableCollection(submittedQueries.values());
  }

  public Collection<QueryInProgress> getRunningQueries() {
    return Collections.unmodifiableCollection(runningQueries.values());
  }

  public Collection<QueryInfo> getFinishedQueries() {
    Set<QueryInfo> result = Sets.newTreeSet();
    synchronized (historyCache) {
      result.addAll(historyCache.values());
    }

    try {
      synchronized (this) {
        result.addAll(this.masterContext.getHistoryReader().getQueries(null));
      }
      return result;
    } catch (Throwable e) {
      LOG.error(e, e);
      return result;
    }
  }

  public QueryInfo getFinishedQuery(QueryId queryId) {
    try {
      QueryInfo queryInfo;
      synchronized (historyCache) {
        queryInfo = (QueryInfo) historyCache.get(queryId);
      }
      if (queryInfo == null) {
        synchronized (this) {
          queryInfo = this.masterContext.getHistoryReader().getQueryInfo(queryId.toString());
        }
      }
      return queryInfo;
    } catch (Throwable e) {
      LOG.error(e.getMessage(), e);
      return null;
    }
  }

  public QueryInfo createNewSimpleQuery(QueryContext queryContext, Session session, String sql, LogicalRootNode plan)
      throws IOException {

    QueryId queryId = QueryIdFactory.newQueryId(masterContext.getResourceManager().getSeedQueryId());
    QueryInProgress queryInProgress = new QueryInProgress(masterContext, session, queryContext, queryId, sql,
        null, plan);
    QueryInfo queryInfo = queryInProgress.getQueryInfo();
    queryInfo.setQueryState(TajoProtos.QueryState.QUERY_SUCCEEDED);
    queryInfo.setFinishTime(System.currentTimeMillis());
    queryInProgress.stopProgress();

    synchronized (historyCache) {
      historyCache.put(queryInfo.getQueryId(), queryInfo);
    }

    return queryInProgress.getQueryInfo();
  }

  /**
   * submit query to scheduler
   */
  public QueryInfo scheduleQuery(Session session, QueryContext queryContext, String sql,
                                 String jsonExpr, LogicalRootNode plan)
      throws Exception {
    QueryId queryId = QueryIdFactory.newQueryId(masterContext.getResourceManager().getSeedQueryId());
    QueryInProgress queryInProgress = new QueryInProgress(masterContext, session, queryContext, queryId, sql,
        jsonExpr, plan);

    queryInProgress.getQueryInfo().setQueryMaster(EMPTY_QM_HOSTNAME);
    submittedQueries.put(queryInProgress.getQueryId(), queryInProgress);
    //TODO implement scheduler queue
    QuerySchedulingInfo querySchedulingInfo = new QuerySchedulingInfo("default", queryContext.getUser(),
        queryInProgress.getQueryId(), 1, queryInProgress.getQueryInfo().getStartTime());

    masterContext.getResourceManager().submitQuery(querySchedulingInfo);
    return queryInProgress.getQueryInfo();
  }

  /**
   * Can start query or not
   */
  public boolean startQueryJob(QueryId queryId, AllocationResourceProto allocation) {

    if (submittedQueries.get(queryId).allocateToQueryMaster(allocation)) {
      QueryInProgress queryInProgress = submittedQueries.remove(queryId);
      runningQueries.put(queryInProgress.getQueryId(), queryInProgress);
      dispatcher.getEventHandler().handle(new QueryJobEvent(QueryJobEvent.Type.QUERY_MASTER_START,
          queryInProgress.getQueryInfo()));
      return true;
    }
    return false;
  }

  class QueryJobManagerEventHandler implements EventHandler<QueryJobEvent> {

    @Override
    public void handle(QueryJobEvent event) {
      QueryInProgress queryInProgress = getQueryInProgress(event.getQueryInfo().getQueryId());

      if (queryInProgress == null) {
        LOG.warn("No query info in running queries.[" + event.getQueryInfo().getQueryId() + "]");
        return;
      }

      if (event.getType() == QueryJobEvent.Type.QUERY_MASTER_START) {
        queryInProgress.submitToQueryMaster();

      } else if (event.getType() == QueryJobEvent.Type.QUERY_JOB_KILL) {

        queryInProgress.kill();
        stopQuery(queryInProgress.getQueryId());

      } else if (event.getType() == QueryJobEvent.Type.QUERY_JOB_HEARTBEAT) {
        queryInProgress.heartbeat(event.getQueryInfo());
      }
    }
  }

  public QueryInProgress getQueryInProgress(QueryId queryId) {
    QueryInProgress queryInProgress;
    queryInProgress = submittedQueries.get(queryId);

    if (queryInProgress == null) {
      queryInProgress = runningQueries.get(queryId);
    }

    return queryInProgress;
  }

  public void stopQuery(QueryId queryId) {
    LOG.info("Stop QueryInProgress:" + queryId);
    QueryInProgress queryInProgress = getQueryInProgress(queryId);
    if(queryInProgress != null) {
      queryInProgress.stopProgress();
      QueryInfo queryInfo = queryInProgress.getQueryInfo();
      synchronized (historyCache) {
        historyCache.put(queryInfo.getQueryId(), queryInfo);
      }

      submittedQueries.remove(queryId);
      runningQueries.remove(queryId);

      long executionTime = queryInfo.getFinishTime() - queryInfo.getStartTime();
      if (executionTime < minExecutionTime.get()) {
        minExecutionTime.set(executionTime);
      }

      if (executionTime > maxExecutionTime.get()) {
        maxExecutionTime.set(executionTime);
      }

      long totalExecutionTime = executedQuerySize.get() * avgExecutionTime.get();
      if (totalExecutionTime > 0) {
        avgExecutionTime.set((totalExecutionTime + executionTime) / (executedQuerySize.get() + 1));
      } else {
        avgExecutionTime.set(executionTime);
      }
      executedQuerySize.incrementAndGet();
    } else {
      LOG.warn("No QueryInProgress while query stopping: " + queryId);
    }
  }

  public long getMinExecutionTime() {
    if (getExecutedQuerySize() == 0) return 0;
    return minExecutionTime.get();
  }

  public long getMaxExecutionTime() {
    return maxExecutionTime.get();
  }

  public long getAvgExecutionTime() {
    return avgExecutionTime.get();
  }

  public long getExecutedQuerySize() {
    return executedQuerySize.get();
  }

  public synchronized TajoHeartbeatResponse.ResponseCommand queryHeartbeat(
      TajoHeartbeatRequest queryHeartbeat) {
    QueryInProgress queryInProgress = getQueryInProgress(new QueryId(queryHeartbeat.getQueryId()));
    if(queryInProgress == null) {
      return null;
    }

    QueryInfo queryInfo = makeQueryInfoFromHeartbeat(queryHeartbeat);
    getEventHandler().handle(new QueryJobEvent(QueryJobEvent.Type.QUERY_JOB_HEARTBEAT, queryInfo));

    return null;
  }

  private QueryInfo makeQueryInfoFromHeartbeat(ResourceProtos.TajoHeartbeatRequest queryHeartbeat) {
    QueryInfo queryInfo = new QueryInfo(new QueryId(queryHeartbeat.getQueryId()));
    WorkerConnectionInfo connectionInfo = new WorkerConnectionInfo(queryHeartbeat.getConnectionInfo());

    queryInfo.setQueryMaster(connectionInfo.getHost());
    queryInfo.setQueryMasterPort(connectionInfo.getQueryMasterPort());
    queryInfo.setQueryMasterclientPort(connectionInfo.getClientPort());
    queryInfo.setLastMessage(queryHeartbeat.getStatusMessage());
    queryInfo.setQueryState(queryHeartbeat.getState());
    queryInfo.setProgress(queryHeartbeat.getQueryProgress());

    if (queryHeartbeat.hasResultDesc()) {
      queryInfo.setResultDesc(new TableDesc(queryHeartbeat.getResultDesc()));
    }

    return queryInfo;
  }
}
