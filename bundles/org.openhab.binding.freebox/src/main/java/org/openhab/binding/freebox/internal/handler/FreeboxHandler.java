/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.freebox.internal.handler;

import static org.openhab.binding.freebox.internal.FreeboxBindingConstants.*;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.binding.BaseBridgeHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.freebox.internal.FreeboxDataListener;
import org.openhab.binding.freebox.internal.api.FreeboxApiManager;
import org.openhab.binding.freebox.internal.api.FreeboxException;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaReceiver;
import org.openhab.binding.freebox.internal.api.model.FreeboxDiscoveryResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapter;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNode;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanHost;
import org.openhab.binding.freebox.internal.config.FreeboxServerConfiguration;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link FreeboxHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Gaël L'hopital - Initial contribution
 * @author Laurent Garnier - updated to a bridge handler and delegate few things to another handler
 * @author Laurent Garnier - update discovery configuration
 * @author Laurent Garnier - use new internal API manager
 */
public class FreeboxHandler extends BaseBridgeHandler {

    private final Logger logger = LoggerFactory.getLogger(FreeboxHandler.class);

    private ScheduledFuture<?> authorizeJob;
    private ScheduledFuture<?> globalJob;
    private FreeboxApiManager apiManager;
    private String fqdn;
    private FreeboxDiscoveryResponse freeboxDiscoveryResponse;
    private List<FreeboxDataListener> dataListeners = new CopyOnWriteArrayList<>();

    public FreeboxHandler(Bridge bridge) {
        super(bridge);

        Bundle bundle = FrameworkUtil.getBundle(getClass());
        String appId = bundle.getSymbolicName();
        String appName = bundle.getHeaders().get("Bundle-Name");
        String appVersion = String.format("%d.%d", bundle.getVersion().getMajor(), bundle.getVersion().getMinor());
        String deviceName = bundle.getHeaders().get("Bundle-Vendor");
        this.apiManager = new FreeboxApiManager(appId, appName, appVersion, deviceName);
    }

    

    @Override
    public void initialize() {
        logger.debug("initializing Freebox Server handler for thing {}", getThing().getUID());

        FreeboxServerConfiguration configuration = getConfigAs(FreeboxServerConfiguration.class);

        // Update the discovery configuration
        Map<String, Object> configDiscovery = new HashMap<String, Object>();
        configDiscovery.put(FreeboxServerConfiguration.DISCOVER_PHONE, configuration.discoverPhone);
        configDiscovery.put(FreeboxServerConfiguration.DISCOVER_NET_DEVICE, configuration.discoverNetDevice);
        configDiscovery.put(FreeboxServerConfiguration.DISCOVER_NET_INTERFACE, configuration.discoverNetInterface);
        configDiscovery.put(FreeboxServerConfiguration.DISCOVER_AIRPLAY_RECEIVER,
                configuration.discoverAirPlayReceiver);
        configDiscovery.put(FreeboxServerConfiguration.DISCOVER_HOME_ADAPTER,
                configuration.discoverHomeAdapter);
        for (FreeboxDataListener dataListener : dataListeners) {
            dataListener.applyConfig(configDiscovery);
        }

        if (StringUtils.isNotEmpty(configuration.fqdn)) {
            updateStatus(ThingStatus.UNKNOWN);

            logger.debug("Binding will schedule a job to establish a connection...");
            if (authorizeJob == null || authorizeJob.isCancelled()) {
                authorizeJob = scheduler.schedule(this::authorize, 1, TimeUnit.SECONDS);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Freebox Server FQDN not set in the thing configuration");
        }
    }

    private void pollServerState() {
        logger.debug("Polling server state...");

        boolean commOk = false;
        try{
            String ip = fqdn.substring(0, fqdn.indexOf(":"));
            logger.debug("ip = "+ip);
            InetAddress inet = InetAddress.getByName(ip);
            commOk = inet.isReachable(10);
        }catch(Exception e){
            logger.error(e.getLocalizedMessage());
            commOk = false;
        }

        if (commOk) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        }
    }

    private void authorize() {
        logger.debug("Authorize job...");

        FreeboxServerConfiguration configuration = getConfigAs(FreeboxServerConfiguration.class);
        fqdn = configuration.fqdn;
        boolean httpsRequestOk = false;
        if (!Boolean.TRUE.equals(configuration.useOnlyHttp)) {
            freeboxDiscoveryResponse = apiManager.checkApi(fqdn, true);
            httpsRequestOk = (freeboxDiscoveryResponse != null);
        }
        if (!httpsRequestOk) {
            freeboxDiscoveryResponse = apiManager.checkApi(fqdn, false);
        }
        boolean useHttps = false;
        String errorMsg = null;
        if (freeboxDiscoveryResponse == null) {
            errorMsg = "Can't connect to " + fqdn;
        } else if (StringUtils.isEmpty(freeboxDiscoveryResponse.getApiBaseUrl())) {
            errorMsg = fqdn + " does not deliver any API base URL";
        } else if (StringUtils.isEmpty(freeboxDiscoveryResponse.getApiVersion())) {
            errorMsg = fqdn + " does not deliver any API version";
        } else if (Boolean.TRUE.equals(freeboxDiscoveryResponse.isHttpsAvailable()) && !Boolean.TRUE.equals(configuration.useOnlyHttp)) {
            if (freeboxDiscoveryResponse.getHttpsPort() == null || StringUtils.isEmpty(freeboxDiscoveryResponse.getApiDomain())) {
                if (httpsRequestOk) {
                    useHttps = true;
                } else {
                    logger.debug("{} does not deliver API domain or HTTPS port; use HTTP API", fqdn);
                }
            } else if (apiManager.checkApi(String.format("%s:%d", freeboxDiscoveryResponse.getApiDomain(), freeboxDiscoveryResponse.getHttpsPort()),
                    true) != null) {
                useHttps = true;
                fqdn = String.format("%s:%d", freeboxDiscoveryResponse.getApiDomain(), freeboxDiscoveryResponse.getHttpsPort());
            }
        }

        if (errorMsg != null) {
            logger.debug("Thing {}: bad configuration: {}", getThing().getUID(), errorMsg);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, errorMsg);
        } else if (!apiManager.authorize(useHttps, fqdn, freeboxDiscoveryResponse.getApiBaseUrl(), freeboxDiscoveryResponse.getApiVersion(),
                configuration.appToken)) {
            if (StringUtils.isEmpty(configuration.appToken)) {
                errorMsg = "App token not set in the thing configuration";
            } else {
                errorMsg = "Check your app token in the thing configuration; opening session with " + fqdn + " using "
                        + (useHttps ? "HTTPS" : "HTTP") + " API version " + freeboxDiscoveryResponse.getApiVersion() + " failed";
            }
            logger.debug("Thing {}: {}", getThing().getUID(), errorMsg);
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, errorMsg);
        } else {
            logger.debug("Thing {}: session opened with {} using {} API version {}", getThing().getUID(), fqdn,
                    (useHttps ? "HTTPS" : "HTTP"), freeboxDiscoveryResponse.getApiVersion());
            if (globalJob == null || globalJob.isCancelled()) {
                long pollingInterval = getConfigAs(FreeboxServerConfiguration.class).refreshInterval;
                logger.debug("Scheduling server state update every {} seconds...", pollingInterval);
                globalJob = scheduler.scheduleWithFixedDelay(() -> {
                    try {
                        pollServerState();
                    } catch (Exception e) {
                        logger.debug("Server state job failed: {}", e.getMessage(), e);
                        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
                    }
                }, 1, pollingInterval, TimeUnit.SECONDS);
            }
        }

        Map<String, String> properties = editProperties();
        if (freeboxDiscoveryResponse != null && StringUtils.isNotEmpty(freeboxDiscoveryResponse.getApiBaseUrl())) {
            properties.put(API_BASE_URL, freeboxDiscoveryResponse.getApiBaseUrl());
        }
        if (freeboxDiscoveryResponse != null && StringUtils.isNotEmpty(freeboxDiscoveryResponse.getApiVersion())) {
            properties.put(API_VERSION, freeboxDiscoveryResponse.getApiVersion());
        }
        if (freeboxDiscoveryResponse != null && StringUtils.isNotEmpty(freeboxDiscoveryResponse.getDeviceType())) {
            properties.put(Thing.PROPERTY_HARDWARE_VERSION, freeboxDiscoveryResponse.getDeviceType());
        }
        updateProperties(properties);
    }

    @Override
    public void dispose() {
        logger.debug("Disposing Freebox Server handler for thing {}", getThing().getUID());
        if (authorizeJob != null && !authorizeJob.isCancelled()) {
            authorizeJob.cancel(true);
            authorizeJob = null;
        }
        if (globalJob != null && !globalJob.isCancelled()) {
            globalJob.cancel(true);
            globalJob = null;
        }
        apiManager.closeSession();
        super.dispose();
    }

    public FreeboxApiManager getApiManager() {
        return apiManager;
    }

    private synchronized List<FreeboxLanHost> fetchLanHosts() {
        try {
            List<FreeboxLanHost> hosts = apiManager.getLanHosts();
            if (hosts == null) {
                hosts = new ArrayList<>();
            }

            // The update of channels is delegated to each thing handler
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof FreeboxThingHandler) {
                    ((FreeboxThingHandler) handler).updateNetInfo(hosts);
                }
            }

            return hosts;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchLanHosts: {}", getThing().getUID(), e.getMessage(), e);
            return null;
        }
    }

    private synchronized List<FreeboxAirMediaReceiver> fetchAirPlayDevices() {
        try {
            List<FreeboxAirMediaReceiver> devices = apiManager.getAirMediaReceivers();
            if (devices == null) {
                devices = new ArrayList<>();
            }

            // The update of channels is delegated to each thing handler
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof FreeboxThingHandler) {
                    ((FreeboxThingHandler) handler).updateAirPlayDevice(devices);
                }
            }

            return devices;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchAirPlayDevices: {}", getThing().getUID(), e.getMessage(), e);
            return null;
        }
    }

    private synchronized void fetchHomeAdapters() {
        try {
            List<FreeboxHomeAdapter> devices = apiManager.getHomeAdapters();
            if (devices == null) {
                devices = new ArrayList<>();
            }

            // The update of channels is delegated to each thing handler
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof FreeboxThingHandler) {
                    ((FreeboxThingHandler) handler).updateHomeAdapters(devices);
                }
            }
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchHomeAdapters: {}", getThing().getUID(), e.getMessage(), e);
        }
    }

    private synchronized void fetchHomeNodes() {
        try {
            List<FreeboxHomeNode> devices = apiManager.getHomeNodes();
            if (devices == null) {
                devices = new ArrayList<>();
            }

            // The update of channels is delegated to each thing handler
            for (Thing thing : getThing().getThings()) {
                ThingHandler handler = thing.getHandler();
                if (handler instanceof FreeboxThingHandler) {
                    ((FreeboxThingHandler) handler).updateHomeNode(devices);
                }
            }
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchHomeAdapters: {}", getThing().getUID(), e.getMessage(), e);
        }
    }



    public void logCommandException(FreeboxException e, ChannelUID channelUID, Command command) {
        if (e.isMissingRights()) {
            logger.debug("Thing {}: missing right {} while handling command {} from channel {}", getThing().getUID(),
                    e.getResponse().getMissingRight(), command, channelUID.getId());
        } else {
            logger.debug("Thing {}: error while handling command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId(), e);
        }
    }

    public FreeboxDiscoveryResponse getFreeboxDiscoveryResponse() {
        return freeboxDiscoveryResponse;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub

    }
}
