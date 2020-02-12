package org.openhab.binding.freebox.internal.handler;

import static org.openhab.binding.freebox.internal.FreeboxBindingConstants.*;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openhab.binding.freebox.internal.api.FreeboxException;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaReceiver;
import org.openhab.binding.freebox.internal.config.FreeboxAirPlayDeviceConfiguration;
import org.openhab.binding.freebox.internal.config.FreeboxNetDeviceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeboxAirMediaHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(FreeboxAirMediaHandler.class);

    private FreeboxHandler freeboxHandler;

    private ScheduledFuture<?> refreshTask;

    private FreeboxAirPlayDeviceConfiguration config;

    public FreeboxAirMediaHandler(Thing thing) {
        super(thing);
        // TODO Auto-generated constructor stub
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        switch (channelUID.getId()) {
            case PLAYURL:
                playMedia(channelUID, command);
                break;
            case STOP:
                stopMedia(channelUID, command);
                break;
            default:
                logger.debug("Thing {}: unexpected command {} from channel {}", getThing().getUID(), command,
                        channelUID.getId());
                break;
        }

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

        config = getConfigAs(FreeboxAirPlayDeviceConfiguration.class);
        logger.debug("FreeboxNetDeviceHandler device config: {}", config);

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
            refreshTask = scheduler.scheduleWithFixedDelay(this::updateAirDevice, 1, getConfigAs(FreeboxNetDeviceConfiguration.class).refreshInterval, TimeUnit.SECONDS);
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

    private void updateAirDevice() {    
        String name = getThing().getProperties().get("name");
        List<FreeboxAirMediaReceiver> receivers = null;
        try {
            receivers = freeboxHandler.getApiManager().getAirMediaReceivers();
        } catch (FreeboxException e) {
            logger.error(e.getLocalizedMessage(), e);
        }
        boolean found = false;
        boolean usable = false;
        if (receivers != null) {
            for (FreeboxAirMediaReceiver receiver : receivers) {
                if (name.equals(receiver.getName())) {
                    found = true;
                    usable = receiver.isVideoCapable();
                    break;
                }
            }
        }
        if (!found) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "AirPlay device not found");
        } else if (!usable) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "AirPlay device without video capability");
        } else {
            updateStatus(ThingStatus.ONLINE);
        }
    }

    private void playMedia(String url) throws FreeboxException {
        if (freeboxHandler != null && url != null) {
            stopMedia();
            freeboxHandler.getApiManager().playMedia(url, getThing().getProperties().get("name"), getThing().getProperties().get("password"));
        }
    }

    private void playMedia(ChannelUID channelUID, Command command) {
        if (command instanceof StringType) {
            try {
                playMedia(command.toString());
            } catch (FreeboxException e) {
                freeboxHandler.logCommandException(e, channelUID, command);
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void stopMedia() throws FreeboxException {
        if (freeboxHandler != null) {
            freeboxHandler.getApiManager().stopMedia(getThing().getProperties().get("name"), getThing().getProperties().get("password"));
        }
    }

    private void stopMedia(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType) {
            try {
                stopMedia();
            } catch (FreeboxException e) {
                freeboxHandler.logCommandException(e, channelUID, command);
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }
    
}