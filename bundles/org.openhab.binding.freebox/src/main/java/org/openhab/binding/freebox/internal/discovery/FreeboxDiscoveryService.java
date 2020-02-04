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
package org.openhab.binding.freebox.internal.discovery;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.smarthome.config.discovery.AbstractDiscoveryService;
import org.eclipse.smarthome.config.discovery.DiscoveryResult;
import org.eclipse.smarthome.config.discovery.DiscoveryResultBuilder;
import org.eclipse.smarthome.config.discovery.DiscoveryService;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingUID;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandlerService;
import org.openhab.binding.freebox.internal.FreeboxBindingConstants;
import org.openhab.binding.freebox.internal.api.FreeboxException;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapter;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNode;
import org.openhab.binding.freebox.internal.api.model.FreeboxType;
import org.openhab.binding.freebox.internal.config.FreeboxHomeAdapterConfiguration;
import org.openhab.binding.freebox.internal.config.FreeboxHomeNodeConfiguration;
import org.openhab.binding.freebox.internal.handler.FreeboxHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * The {@link FreeboxDiscoveryService} is responsible for discovering all things
 * except the Freebox Server thing itself
 *
 * @author Laurent Garnier - Initial contribution
 * @author Laurent Garnier - add discovery settings
 * @author Laurent Garnier - use new internal API manager
 */
public class FreeboxDiscoveryService extends AbstractDiscoveryService implements DiscoveryService, ThingHandlerService {

    private final Logger logger = LoggerFactory.getLogger(FreeboxDiscoveryService.class);

    private static final int SEARCH_TIME = 10;

    private ScheduledFuture<?> scanTask;

    private static final String PHONE_ID = "wired";

    private FreeboxHandler bridgeHandler;
    private boolean discoverPhone;
    private boolean discoverNetDevice;
    private boolean discoverNetInterface;
    private boolean discoverAirPlayReceiver;
    private boolean discoverHomeAdapter;

    /**
     * Creates a FreeboxDiscoveryService with background discovery disabled.
     */
    public FreeboxDiscoveryService(FreeboxHandler freeboxBridgeHandler) {
        super(FreeboxBindingConstants.SUPPORTED_THING_TYPES_UIDS, SEARCH_TIME, false);
        this.bridgeHandler = freeboxBridgeHandler;
        this.discoverPhone = true;
        this.discoverNetDevice = true;
        this.discoverNetInterface = true;
        this.discoverAirPlayReceiver = true;
        this.discoverHomeAdapter = false;
    }

    @Override
    public void activate(@Nullable Map<@NonNull String, @Nullable Object> configProperties) {
        super.activate(configProperties);
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }


    @Override
    protected void startScan() {
        logger.debug("Starting Freebox discovery scan");
        if (bridgeHandler != null && bridgeHandler.getThing().getStatus() == ThingStatus.ONLINE) {
            if (this.scanTask != null) {
                scanTask.cancel(true);
            }
            this.scanTask = scheduler.schedule(() -> discoverFreebox(), 0, TimeUnit.SECONDS);
        }
    }

    @Override
    protected void stopScan() {
        super.stopScan();

        if (this.scanTask != null) {
            this.scanTask.cancel(true);
            this.scanTask = null;
        }
    }

    private void discoverFreebox(){
        if (bridgeHandler != null && bridgeHandler.getThing().getStatus() == ThingStatus.ONLINE) {
            ThingUID thingUID;
            ThingUID bridge = bridgeHandler.getThing().getUID();
            DiscoveryResult discoveryResult;
            if(bridgeHandler.getFreeboxDiscoveryResponse().getBox_model().equals(FreeboxType.FREEBOX_DELTA_R1.getName())){
                thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_DELTA_SERVER, bridge, "Delta");
                discoveryResult = DiscoveryResultBuilder.create(thingUID).withBridge(bridge).withLabel("Freebox Delta")
                .build();
                thingDiscovered(discoveryResult);
            }
            if(bridgeHandler.getApiManager().getFreeboxPermissions().istHomeAllowed()){
                List<FreeboxHomeAdapter> freeboxHomeAdapters = new ArrayList<FreeboxHomeAdapter>();
                try {
                    freeboxHomeAdapters = bridgeHandler.getApiManager().getHomeAdapters();
                } catch (FreeboxException e) {
                    logger.debug(e.getMessage());
                }
                for (FreeboxHomeAdapter homeAdapter : freeboxHomeAdapters) {
                    String name = homeAdapter.getType().getName();
                    if(StringUtils.isNotEmpty(name)){
                        String uid = name.replaceAll(":", "_");
                        thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_HOME_ADAPTER, bridge, uid);
                        logger.trace("Adding new Freebox Home Adapter {} to inbox", thingUID);
                        Map<String, Object> properties = new HashMap<>();
                        properties.put(FreeboxHomeAdapterConfiguration.NAME, homeAdapter.getLabel());
                        properties.put(FreeboxHomeNodeConfiguration.ID, homeAdapter.getId());
                        discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                                .withBridge(bridge).withLabel(name + " (Home Adapter)").build();
                        thingDiscovered(discoveryResult);
                    }
                }
                List<FreeboxHomeNode> freeboxHomeNodes = new ArrayList<FreeboxHomeNode>();
                try {
                    freeboxHomeNodes = bridgeHandler.getApiManager().getHomeNodes();
                } catch (FreeboxException e) {
                    logger.debug(e.getMessage());
                }
                for (FreeboxHomeNode freeboxHomeNode : freeboxHomeNodes) {
                    String name = freeboxHomeNode.getName();
                    logger.debug("Node : "+name);
                    if(StringUtils.isNotEmpty(name)){
                        String uid = name.replaceAll(":", "_");
                        if(freeboxHomeNode.getCategory().equals("dws")){
                            thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_HOME_DOOR_SENSOR, bridge, uid);
                        }else 
                            continue;
                        logger.trace("Adding new Freebox Home Node {} to inbox", thingUID);
                        Map<String, Object> properties = new HashMap<>();
                        properties.put(FreeboxHomeNodeConfiguration.NAME, freeboxHomeNode.getName());
                        properties.put(FreeboxHomeNodeConfiguration.ID, freeboxHomeNode.getId());
                        discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                                .withBridge(bridge).withLabel(freeboxHomeNode.getLabel() + " (Home Node)").build();
                        thingDiscovered(discoveryResult);
                    }
                }
            }
        
        }
    }
/**
    @Override
    public void onDataFetched(ThingUID bridge, List<FreeboxLanHost> lanHosts,
            List<FreeboxAirMediaReceiver> airPlayDevices) {
        if (bridge == null) {
            return;
        }

        ThingUID thingUID;
        DiscoveryResult discoveryResult;

        if (discoverPhone) {
            // Phone
            thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_PHONE, bridge, PHONE_ID);
            logger.trace("Adding new Freebox Phone {} to inbox", thingUID);
            discoveryResult = DiscoveryResultBuilder.create(thingUID).withBridge(bridge).withLabel("Wired phone")
                    .build();
            thingDiscovered(discoveryResult);
        }

        if (lanHosts != null && (discoverNetDevice || discoverNetInterface)) {
            // Network devices
            for (FreeboxLanHost host : lanHosts) {
                String mac = host.getMAC();
                if (StringUtils.isNotEmpty(mac)) {
                    if (discoverNetDevice) {
                        String uid = mac.replaceAll("[^A-Za-z0-9_]", "_");
                        thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_NET_DEVICE, bridge, uid);
                        String name = StringUtils.isEmpty(host.getPrimaryName()) ? ("Freebox Network Device " + mac)
                                : host.getPrimaryName();
                        logger.trace("Adding new Freebox Network Device {} to inbox", thingUID);
                        Map<String, Object> properties = new HashMap<>(1);
                        if (StringUtils.isNotEmpty(host.getVendorName())) {
                            properties.put(Thing.PROPERTY_VENDOR, host.getVendorName());
                        }
                        properties.put(FreeboxNetDeviceConfiguration.MAC_ADDRESS, mac);
                        discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                                .withBridge(bridge).withLabel(name).build();
                        thingDiscovered(discoveryResult);
                    }

                    // Network interfaces
                    if (host.getL3Connectivities() != null && discoverNetInterface) {
                        for (FreeboxLanHostL3Connectivity l3 : host.getL3Connectivities()) {
                            String addr = l3.getAddr();
                            if (StringUtils.isNotEmpty(addr)) {
                                String uid = addr.replaceAll("[^A-Za-z0-9_]", "_");
                                thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_NET_INTERFACE,
                                        bridge, uid);
                                String name = addr;
                                if (StringUtils.isNotEmpty(host.getPrimaryName())) {
                                    name += " (" + (host.getPrimaryName() + ")");
                                }
                                logger.trace("Adding new Freebox Network Interface {} to inbox", thingUID);
                                Map<String, Object> properties = new HashMap<>(1);
                                if (StringUtils.isNotEmpty(host.getVendorName())) {
                                    properties.put(Thing.PROPERTY_VENDOR, host.getVendorName());
                                }
                                properties.put(FreeboxNetInterfaceConfiguration.IP_ADDRESS, addr);
                                discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                                        .withBridge(bridge).withLabel(name).build();
                                thingDiscovered(discoveryResult);
                            }
                        }
                    }
                }
            }
        }

        if (airPlayDevices != null && discoverAirPlayReceiver) {
            // AirPlay devices
            for (FreeboxAirMediaReceiver device : airPlayDevices) {
                String name = device.getName();
                boolean videoCapable = device.isVideoCapable();
                logger.debug("AirPlay Device name {} video capable {}", name, videoCapable);
                // The Freebox API allows pushing media only to receivers with photo or video capabilities
                // but not to receivers with only audio capability; so receivers without video capability
                // are ignored by the discovery
                if (StringUtils.isNotEmpty(name) && videoCapable) {
                    String uid = name.replaceAll("[^A-Za-z0-9_]", "_");
                    thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_AIRPLAY, bridge, uid);
                    logger.trace("Adding new Freebox AirPlay Device {} to inbox", thingUID);
                    Map<String, Object> properties = new HashMap<>(1);
                    properties.put(FreeboxAirPlayDeviceConfiguration.NAME, name);
                    discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                            .withBridge(bridge).withLabel(name + " (AirPlay)").build();
                    thingDiscovered(discoveryResult);
                }
            }
        }
        if (discoverHomeAdapter) {
            List<FreeboxHomeAdapter> freeboxHomeAdapters = new ArrayList<FreeboxHomeAdapter>();
            try {
                freeboxHomeAdapters = bridgeHandler.getApiManager().getHomeAdapters();
            } catch (FreeboxException e) {
                logger.debug(e.getMessage());
            }
            for (FreeboxHomeAdapter homeAdapter : freeboxHomeAdapters) {
                String name = homeAdapter.getType().getName();
                if(StringUtils.isNotEmpty(name)){
                    String uid = name.replaceAll(":", "_");
                    thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_HOME_ADAPTER, bridge, uid);
                    logger.trace("Adding new Freebox Home Adapter {} to inbox", thingUID);
                    Map<String, Object> properties = new HashMap<>();
                    properties.put(FreeboxHomeAdapterConfiguration.NAME, homeAdapter.getLabel());
                    discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                            .withBridge(bridge).withLabel(name + " (Home Adapter)").build();
                    thingDiscovered(discoveryResult);
                }
            }
            List<FreeboxHomeNode> freeboxHomeNodes = new ArrayList<FreeboxHomeNode>();
            try {
                freeboxHomeNodes = bridgeHandler.getApiManager().getHomeNodes();
            } catch (FreeboxException e) {
                logger.debug(e.getMessage());
            }
            for (FreeboxHomeNode freeboxHomeNode : freeboxHomeNodes) {
                String name = freeboxHomeNode.getName();
                logger.debug("Node : "+name);
                if(StringUtils.isNotEmpty(name)){
                    String uid = name.replaceAll(":", "_");
                    if(freeboxHomeNode.getCategory().equals("dws")){
                        thingUID = new ThingUID(FreeboxBindingConstants.FREEBOX_THING_TYPE_HOME_DOOR_SENSOR, bridge, uid);
                    }else 
                        continue;
                    logger.trace("Adding new Freebox Home Node {} to inbox", thingUID);
                    Map<String, Object> properties = new HashMap<>();
                    properties.put(FreeboxHomeNodeConfiguration.NAME, freeboxHomeNode.getName());
                    properties.put(FreeboxHomeNodeConfiguration.ID, freeboxHomeNode.getId());
                    discoveryResult = DiscoveryResultBuilder.create(thingUID).withProperties(properties)
                            .withBridge(bridge).withLabel(freeboxHomeNode.getLabel() + " (Home Node)").build();
                    thingDiscovered(discoveryResult);
                }
            }
        }
       

    }
        **/

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        if (handler instanceof FreeboxHandler) {
            bridgeHandler = (FreeboxHandler) handler;
        }
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return bridgeHandler;
    }
}
