package org.openhab.binding.freebox.internal.handler;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

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
import org.openhab.binding.freebox.internal.api.FreeboxException;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapter;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanHost;
import org.openhab.binding.freebox.internal.config.FreeboxHomeAdapterConfiguration;
import org.openhab.binding.freebox.internal.config.FreeboxNetInterfaceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeboxNetInterfaceHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(FreeboxHomeDoorSensorHandler.class);

    private FreeboxHandler freeboxHandler;

    private ScheduledFuture<?> refreshTask;

    private FreeboxNetInterfaceConfiguration config;

    public FreeboxNetInterfaceHandler(Thing thing) {
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
            startAutomaticRefresh();
            return;
        }

        config = getConfigAs(FreeboxNetInterfaceConfiguration.class);
        logger.debug("FreeboxNetInterfaceHandler device config: {}", config);

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

    private void startAutomaticRefresh() {
        if (refreshTask == null || refreshTask.isCancelled()) {
            refreshTask = scheduler.scheduleWithFixedDelay(this::updateNetDevice, 1, getConfigAs(FreeboxNetInterfaceConfiguration.class).refreshInterval, TimeUnit.SECONDS);
        }
    }

    private void initializeBridge(ThingHandler thingHandler, ThingStatus bridgeStatus) {
        logger.debug("initializeBridge {} for thing {}", bridgeStatus, getThing().getUID());

        if (thingHandler != null && bridgeStatus != null) {
            freeboxHandler = (FreeboxHandler) thingHandler;

            if (bridgeStatus == ThingStatus.ONLINE) {
                updateStatus(ThingStatus.ONLINE);
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            }
        } else {
            updateStatus(ThingStatus.OFFLINE);
        }
    }

    private void updateNetDevice() {    
        String id = getThing().getProperties().get("id");
        String _interface = getThing().getProperties().get("_interface");

        FreeboxLanHost host = null;
        try{
            host = freeboxHandler.getApiManager().getLanHostsFromInterface(_interface, id);
        }catch(FreeboxException e){
            logger.error("updateNetDevice error: {}", e.getLocalizedMessage());
        }
        
        if (host == null){
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Net interface not found");
        } else {
            updateState(new ChannelUID(getThing().getUID(), FreeboxBindingConstants.ADAPTER_ACTIVE), host.isActive() ? OnOffType.ON : OnOffType.OFF);
        }
    }
    
}