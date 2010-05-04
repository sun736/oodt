/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package gov.nasa.jpl.oodt.cas.catalog.server.channel;

//OODT imports
import gov.nasa.jpl.oodt.cas.catalog.system.CatalogServiceFactory;

/**
 * @author bfoster
 * @version $Revision$
 *
 * <p>
 * A Factory for creating CommunicationChannelServer
 * <p>
 */
public interface CommunicationChannelServerFactory {
	
	public CommunicationChannelServer createCommunicationChannelServer();
	
	public void setPort(int port);
	
	public int getPort();
	
	public void setCatalogServiceFactory(CatalogServiceFactory catalogServiceFactory);
	
}