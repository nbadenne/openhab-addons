package org.openhab.binding.freebox.internal.handler;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.freebox.internal.FreeboxBindingConstants;
import org.openhab.binding.freebox.internal.api.FreeboxApiManager;
import org.openhab.binding.freebox.internal.api.FreeboxException;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapter;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNode;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNodeEndpoint;
import org.openhab.binding.freebox.internal.config.FreeboxHomeAdapterConfiguration;
import org.openhab.binding.freebox.internal.config.FreeboxHomeNodeConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeboxHomeDoorSensorHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(FreeboxHomeDoorSensorHandler.class);

    private FreeboxApiManager apiManager;

    private FreeboxHandler freeboxHandler;

    private ScheduledFuture<?> refreshTask;

    private FreeboxHomeAdapterConfiguration config;

    public FreeboxHomeDoorSensorHandler(Thing thing) {
        super(thing);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // TODO Auto-generated method stub

    }

    @Override
    public void dispose() {
        logger.debug("Running dispose()");
        if (refreshTask != null) {
            refreshTask.cancel(true);
            refreshTask = null;
        }
        freeboxHandler = null;
    }

    @Override
    public void initialize() {
        logger.debug("Initializing {} handler.", getThing().getThingTypeUID());

        Bridge bridge = getBridge();
        if (bridge == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Bridge Not set");
            return;
        }

        config = getConfigAs(FreeboxHomeAdapterConfiguration.class);
        logger.debug("FreeboxHomeAdapter device config: {}", config);

        initializeBridge(bridge.getHandler(), bridge.getStatus());
    }

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {
        logger.debug("bridgeStatusChanged {} for thing {}", bridgeStatusInfo, getThing().getUID());
        Bridge bridge = getBridge();
        if (bridge != null) {
            initializeBridge(bridge.getHandler(), bridgeStatusInfo.getStatus());
        }
    }

    private void initializeBridge(ThingHandler thingHandler, ThingStatus bridgeStatus) {
        logger.debug("initializeBridge {} for thing {}", bridgeStatus, getThing().getUID());

        if (thingHandler != null && bridgeStatus != null) {
            freeboxHandler = (FreeboxHandler) thingHandler;

            if (bridgeStatus == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
                startAutomaticRefresh();
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    private void startAutomaticRefresh() {
        if (refreshTask == null || refreshTask.isCancelled()) {
            refreshTask = scheduler.scheduleWithFixedDelay(this::updateHomeDoorSensor, 1, getConfigAs(FreeboxHomeNodeConfiguration.class).refreshInterval, TimeUnit.SECONDS);
        }
    }

    private void updateHomeDoorSensor() {
        int id = (int) Double.parseDouble(getThing().getProperties().get("id"));
        FreeboxHomeNodeEndpoint freeboxHomeNodeEndpointOpen = null;
        FreeboxHomeNodeEndpoint freeboxHomeNodeEndpointBattery = null;
        try {
            freeboxHomeNodeEndpointOpen = freeboxHandler.getApiManager().getHomeEndpointStatus(id, 7);
            freeboxHomeNodeEndpointBattery = freeboxHandler.getApiManager().getHomeEndpointStatus(id,
                8);
        } catch (FreeboxException e) {
            logger.error("updateHomeDoorSensor error: {}", e.getLocalizedMessage());
        }
        if (freeboxHomeNodeEndpointOpen == null || freeboxHomeNodeEndpointBattery == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Home node not found");
        } else {
            updateState(new ChannelUID(getThing().getUID(), FreeboxBindingConstants.DOOR_OPEN),
                    (boolean) freeboxHomeNodeEndpointOpen.getValue() ? OnOffType.ON : OnOffType.OFF);
            updateState(new ChannelUID(getThing().getUID(), FreeboxBindingConstants.BATTTERY_LEVEL),
                    new DecimalType((double) freeboxHomeNodeEndpointBattery.getValue()));
        }
    }

    public void updateHomeNode(List<FreeboxHomeNode> freeboxHomeNodes) {
        String name = getThing().getProperties().get("name");
        boolean found = false;
        boolean active = false;
        if (freeboxHomeNodes != null) {
            for (FreeboxHomeNode homeNode : freeboxHomeNodes) {
                if (name.equals(homeNode.getName())) {
                    found = true;
                    if (homeNode.getStatus().equals("active")) {
                        active = true;
                    }
                    break;
                }
            }
        }

        if (!found) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Home node not found");
        } else {
            updateState(new ChannelUID(getThing().getUID(), FreeboxBindingConstants.NODE_ACTIVE), active ? OnOffType.ON : OnOffType.OFF);
        }
    }
    
}