/*
 * Copyright (C) 2017 Dremio Corporation
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
package com.dremio.dac.server;

import com.dremio.common.config.SabotConfig;
import com.dremio.config.DremioConfig;
import com.dremio.dac.daemon.DACDaemon.ClusterMode;

/**
 * Daemon configuration pojo facade. Look to replace with tscfg
 */
public final class DacConfig {

  public final boolean autoPort;
  public final boolean sendStackTraceToClient;
  public final boolean prettyPrintJSON;
  public final boolean verboseAccessLog;
  public final boolean allowTestApis;
  public final boolean serveUI;
  public final boolean webSSLEnabled;
  public final boolean prepopulate;
  public final boolean addDefaultUser;
  public final boolean allowNewerKVStore;

  // private due to checkstyle.
  private final ClusterMode clusterMode;
  public final String masterNode;
  public final int masterPort;
  public final int localPort;
  public final boolean inMemoryStorage;
  public final boolean isMaster;
  public final boolean isRemote;

  private final DremioConfig config;

  public DacConfig(DremioConfig config) {
    // default values
    this(
      config.getBoolean(DremioConfig.DEBUG_AUTOPORT_BOOL),
      config.getBoolean(DremioConfig.DEBUG_ENABLED_BOOL),
      config.getBoolean(DremioConfig.DEBUG_ENABLED_BOOL),
      config.getBoolean(DremioConfig.DEBUG_ENABLED_BOOL),
      config.getBoolean(DremioConfig.DEBUG_ALLOW_TEST_APIS_BOOL),
      config.getBoolean(DremioConfig.WEB_ENABLED_BOOL),
      config.getBoolean(DremioConfig.WEB_SSL_ENABLED_BOOL),
      config.getBoolean(DremioConfig.DEBUG_PREPOPULATE_BOOL),
      config.getBoolean(DremioConfig.DEBUG_SINGLE_NODE_BOOL) ? ClusterMode.LOCAL : ClusterMode.DISTRIBUTED,
      config.getString(DremioConfig.MASTER_NODE_STRING),
      config.getInt(DremioConfig.MASTER_PORT_INT),
      config.getInt(DremioConfig.SERVER_PORT_INT),
      config.getBoolean(DremioConfig.DEBUG_USE_MEMORY_STRORAGE_BOOL),
      config.getBoolean(DremioConfig.DEBUG_FORCE_MASTER_BOOL),
      config.getBoolean(DremioConfig.DEBUG_FORCE_REMOTE_BOOL),
      config.getBoolean(DremioConfig.DEBUG_ADD_DEFAULT_USER),
      config.getBoolean(DremioConfig.DEBUG_ALLOW_NEWER_KVSTORE),
      config
     );
  }

  private DacConfig(
    boolean autoPort,
    boolean sendStackTraceToClient,
    boolean prettyPrintJSON,
    boolean verboseAccessLog,
    boolean allowTestApis,
    boolean serveUI,
    boolean webSSLEnabled,
    boolean prepopulate,
    ClusterMode clusterMode,
    String masterNode,
    int masterPort,
    int localPort,
    boolean inMemoryStorage,
    boolean forceMaster,
    boolean forceRemote,
    boolean addDefaultUser,
    boolean allowNewerKVStore,
    DremioConfig config
  ) {
    super();
    this.autoPort = autoPort;
    this.sendStackTraceToClient = sendStackTraceToClient;
    this.prettyPrintJSON = prettyPrintJSON;
    this.verboseAccessLog = verboseAccessLog;
    this.allowTestApis = allowTestApis;
    this.serveUI = serveUI;
    this.webSSLEnabled = webSSLEnabled;
    this.prepopulate = prepopulate;
    this.clusterMode = clusterMode;
    this.masterNode = masterNode;
    this.inMemoryStorage = inMemoryStorage;
    this.masterPort = masterPort;
    this.localPort = localPort;
    this.config = config;
    this.isMaster = forceMaster;
    this.isRemote = forceRemote;
    this.addDefaultUser = addDefaultUser;
    this.allowNewerKVStore = allowNewerKVStore;
  }

  public DacConfig with(String path, Object value){
    return new DacConfig(config.withValue(path, value));
  }

  public DacConfig debug(boolean debug) {
    return with(DremioConfig.DEBUG_ENABLED_BOOL, debug);
  }

  public DacConfig autoPort(boolean autoPort) {
    return with(DremioConfig.DEBUG_AUTOPORT_BOOL, autoPort);
  }

  public int getHttpPort(){
    return config.getInt(DremioConfig.WEB_PORT_INT);
  }

  public DacConfig allowTestApis(boolean allowTestApis) {
    return with(DremioConfig.DEBUG_ALLOW_TEST_APIS_BOOL, allowTestApis);
  }

  public DacConfig addDefaultUser(boolean addDefaultUser) {
    return with(DremioConfig.DEBUG_ADD_DEFAULT_USER, addDefaultUser);
  }

  public DacConfig serveUI(boolean serveUI) {
    return with(DremioConfig.WEB_ENABLED_BOOL, serveUI);
  }

  public DacConfig webSSLEnabled(boolean webSSLEnabled) {
    return with(DremioConfig.WEB_SSL_ENABLED_BOOL, webSSLEnabled);
  }

  public DacConfig prepopulate(boolean prepopulate) {
    return with(DremioConfig.DEBUG_PREPOPULATE_BOOL, prepopulate);
  }

  public DacConfig writePath(String writePath) {
    return with(DremioConfig.LOCAL_WRITE_PATH_STRING, writePath);
  }

  public DacConfig clusterMode(ClusterMode clusterMode) {
    return with(DremioConfig.DEBUG_SINGLE_NODE_BOOL, clusterMode == ClusterMode.LOCAL);
  }

  public DacConfig masterNode(String masterNode) {
    return with(DremioConfig.MASTER_NODE_STRING, masterNode);
  }

  public DacConfig masterPort(int port) {
    return with(DremioConfig.MASTER_PORT_INT, port);
  }

  public DacConfig localPort(int port) {
    return with(DremioConfig.SERVER_PORT_INT, port);
  }

  public DacConfig zk(String quorum) {
    return with(DremioConfig.ZOOKEEPER_QUORUM, quorum);
  }

  public DacConfig httpPort(int httpPort) {
    return with(DremioConfig.WEB_PORT_INT, httpPort);
  }

  public DacConfig inMemoryStorage(boolean inMemoryStorage) {
    return with(DremioConfig.DEBUG_USE_MEMORY_STRORAGE_BOOL, inMemoryStorage);
  }

  public DacConfig isRemote(boolean isRemote) {
    return with(DremioConfig.DEBUG_FORCE_REMOTE_BOOL, isRemote);
  }

  public boolean isAutoUpgrade() {
    return config.getBoolean(DremioConfig.AUTOUPGRADE);
  }

  public DacConfig autoUpgrade(boolean value) {
    return with(DremioConfig.AUTOUPGRADE, value);
  }

  public ClusterMode getClusterMode() {
    return this.clusterMode;
  }

  public String getMasterNode() {
    return (this.clusterMode == ClusterMode.LOCAL) ? "localhost" : this.masterNode;
  }

  public DremioConfig getConfig(){
    return config;
  }

  public static DacConfig newConfig() {
    return new DacConfig(DremioConfig.create());
  }

  public static DacConfig newDebugConfig(SabotConfig config) {
    return new DacConfig(
        DremioConfig.create(null, config)
        ).debug(true);
  }
}