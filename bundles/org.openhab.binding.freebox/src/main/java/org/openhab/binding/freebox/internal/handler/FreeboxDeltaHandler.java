package org.openhab.binding.freebox.internal.handler;

import static org.openhab.binding.freebox.internal.FreeboxBindingConstants.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.IncreaseDecreaseType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.library.types.OpenClosedType;
import org.eclipse.smarthome.core.library.types.PercentType;
import org.eclipse.smarthome.core.library.types.StringType;
import org.eclipse.smarthome.core.library.types.UpDownType;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.binding.BaseThingHandler;
import org.eclipse.smarthome.core.thing.binding.ThingHandler;
import org.eclipse.smarthome.core.types.Command;
import org.eclipse.smarthome.core.types.RefreshType;
import org.openhab.binding.freebox.internal.api.FreeboxApiManager;
import org.openhab.binding.freebox.internal.api.FreeboxException;
import org.openhab.binding.freebox.internal.api.model.FreeboxConnectionStatus;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapter;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNode;
import org.openhab.binding.freebox.internal.api.model.FreeboxLcdConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxSambaConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxSystemConfig;
import org.openhab.binding.freebox.internal.config.FreeboxDeltaConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeboxDeltaHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(FreeboxDeltaHandler.class);

    private FreeboxApiManager apiManager;

    private FreeboxHandler freeboxHandler;

    private ScheduledFuture<?> refreshTask;

    private FreeboxDeltaConfiguration config;

    private long uptime;

    public FreeboxDeltaHandler(Thing thing) {
        super(thing);
        uptime = -1;
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        if (command instanceof RefreshType) {
            return;
        }
        if (getThing().getStatus() == ThingStatus.UNKNOWN || (getThing().getStatus() == ThingStatus.OFFLINE
                && getThing().getStatusInfo().getStatusDetail() == ThingStatusDetail.CONFIGURATION_ERROR)) {
            return;
        }
        switch (channelUID.getId()) {
            case LCDBRIGHTNESS:
                setBrightness(channelUID, command);
                break;
            case LCDORIENTATION:
                setOrientation(channelUID, command);
                break;
            case LCDFORCED:
                setForced(channelUID, command);
                break;
            case WIFISTATUS:
                setWifiStatus(channelUID, command);
                break;
            case FTPSTATUS:
                setFtpStatus(channelUID, command);
                break;
            case AIRMEDIASTATUS:
                setAirMediaStatus(channelUID, command);
                break;
            case UPNPAVSTATUS:
                setUPnPAVStatus(channelUID, command);
                break;
            case SAMBAFILESTATUS:
                setSambaFileStatus(channelUID, command);
                break;
            case SAMBAPRINTERSTATUS:
                setSambaPrinterStatus(channelUID, command);
                break;
            case REBOOT:
                reboot(channelUID, command);
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
            return;
        }

        config = getConfigAs(FreeboxDeltaConfiguration.class);
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
            freeboxHandler =  (FreeboxHandler) thingHandler;
            apiManager = freeboxHandler.getApiManager();

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
            refreshTask = scheduler.scheduleWithFixedDelay(this::pollServerState, 1,
                    config.pollingInterval, TimeUnit.SECONDS);
        }
    }

    private void pollServerState() {
        logger.debug("Polling server state...");

        boolean commOk = true;
        commOk &= fetchSystemConfig();
        commOk &= fetchLCDConfig();
        commOk &= fetchWifiConfig();
        commOk &= fetchConnectionStatus();
        commOk &= fetchFtpConfig();
        commOk &= fetchAirMediaConfig();
        commOk &= fetchUPnPAVConfig();
        commOk &= fetchSambaConfig();
        if (commOk) {
            updateStatus(ThingStatus.ONLINE);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
        }
    }

    


    private boolean fetchConnectionStatus() {
        boolean result = true;
        try {
            FreeboxConnectionStatus connectionStatus = apiManager.getConnectionStatus();
            if (StringUtils.isNotEmpty(connectionStatus.getState())) {
                updateChannelStringState(LINESTATUS, connectionStatus.getState());
            }
            if (StringUtils.isNotEmpty(connectionStatus.getIpv4())) {
                updateChannelStringState(IPV4, connectionStatus.getIpv4());
            }
            if(connectionStatus.getMedia().equals("xdsl")){
                result &= fetchxDslStatus();
            }
            updateChannelDecimalState(RATEUP, connectionStatus.getRateUp());
            updateChannelDecimalState(RATEDOWN, connectionStatus.getRateDown());
            updateChannelDecimalState(BYTESUP, connectionStatus.getBytesUp());
            updateChannelDecimalState(BYTESDOWN, connectionStatus.getBytesDown());
            return result;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchConnectionStatus: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchxDslStatus() {
        try {
            String status = apiManager.getxDslStatus();
            if (StringUtils.isNotEmpty(status)) {
                updateChannelStringState(XDSLSTATUS, status);
            }
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchxDslStatus: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchWifiConfig() {
        try {
            updateChannelSwitchState(WIFISTATUS, apiManager.isWifiEnabled());
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchWifiConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchFtpConfig() {
        try {
            updateChannelSwitchState(FTPSTATUS, apiManager.isFtpEnabled());
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchFtpConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchAirMediaConfig() {
        try {
            if (!apiManager.isInLanBridgeMode()) {
                updateChannelSwitchState(AIRMEDIASTATUS, apiManager.isAirMediaEnabled());
            }
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchAirMediaConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchUPnPAVConfig() {
        try {
            if (!apiManager.isInLanBridgeMode()) {
                updateChannelSwitchState(UPNPAVSTATUS, apiManager.isUPnPAVEnabled());
            }
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchUPnPAVConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchSambaConfig() {
        try {
            FreeboxSambaConfig config = apiManager.getSambaConfig();
            updateChannelSwitchState(SAMBAFILESTATUS, config.isFileShareEnabled());
            updateChannelSwitchState(SAMBAPRINTERSTATUS, config.isPrintShareEnabled());
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchSambaConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchLCDConfig() {
        try {
            FreeboxLcdConfig config = apiManager.getLcdConfig();
            updateChannelDecimalState(LCDBRIGHTNESS, config.getBrightness());
            updateChannelDecimalState(LCDORIENTATION, config.getOrientation());
            updateChannelSwitchState(LCDFORCED, config.isOrientationForced());
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchLCDConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private boolean fetchSystemConfig() {
        try {
            FreeboxSystemConfig config = apiManager.getSystemConfig();
            Map<String, String> properties = editProperties();
            if (StringUtils.isNotEmpty(config.getSerial())) {
                properties.put(Thing.PROPERTY_SERIAL_NUMBER, config.getSerial());
            }
            if (StringUtils.isNotEmpty(config.getBoardName())) {
                properties.put(Thing.PROPERTY_HARDWARE_VERSION, config.getBoardName());
            }
            if (StringUtils.isNotEmpty(config.getFirmwareVersion())) {
                properties.put(Thing.PROPERTY_FIRMWARE_VERSION, config.getFirmwareVersion());
                updateChannelStringState(FWVERSION, config.getFirmwareVersion());
            }
            if (StringUtils.isNotEmpty(config.getMac())) {
                properties.put(Thing.PROPERTY_MAC_ADDRESS, config.getMac());
            }
            updateProperties(properties);

            long newUptime = config.getUptimeVal();
            updateChannelSwitchState(RESTARTED, newUptime < uptime);
            uptime = newUptime;

            updateChannelDecimalState(UPTIME, uptime);
            updateChannelDecimalState(TEMPCPUM, config.getTempCpum());
            updateChannelDecimalState(TEMPCPUB, config.getTempCpub());
            updateChannelDecimalState(TEMPSWITCH, config.getTempSw());
            updateChannelDecimalState(FANSPEED, config.getFanRpm());
            return true;
        } catch (FreeboxException e) {
            logger.debug("Thing {}: exception in fetchSystemConfig: {}", getThing().getUID(), e.getMessage(), e);
            return false;
        }
    }

    private void setBrightness(ChannelUID channelUID, Command command) {
        try {
            if (command instanceof IncreaseDecreaseType) {
                if (command == IncreaseDecreaseType.INCREASE) {
                    updateChannelDecimalState(LCDBRIGHTNESS, apiManager.increaseLcdBrightness());
                } else {
                    updateChannelDecimalState(LCDBRIGHTNESS, apiManager.decreaseLcdBrightness());
                }
            } else if (command instanceof OnOffType) {
                updateChannelDecimalState(LCDBRIGHTNESS,
                        apiManager.setLcdBrightness((command == OnOffType.ON) ? 100 : 0));
            } else if (command instanceof DecimalType) {
                updateChannelDecimalState(LCDBRIGHTNESS,
                        apiManager.setLcdBrightness(((DecimalType) command).intValue()));
            } else if (command instanceof PercentType) {
                updateChannelDecimalState(LCDBRIGHTNESS,
                        apiManager.setLcdBrightness(((PercentType) command).intValue()));
            } else {
                logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                        channelUID.getId());
            }
        } catch (FreeboxException e) {
            logCommandException(e, channelUID, command);
            fetchLCDConfig();
        }
    }

    private void setOrientation(ChannelUID channelUID, Command command) {
        if (command instanceof DecimalType) {
            try {
                FreeboxLcdConfig config = apiManager.setLcdOrientation(((DecimalType) command).intValue());
                updateChannelDecimalState(LCDORIENTATION, config.getOrientation());
                updateChannelSwitchState(LCDFORCED, config.isOrientationForced());
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchLCDConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setForced(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                updateChannelSwitchState(LCDFORCED, apiManager.setLcdOrientationForced(command.equals(OnOffType.ON)
                        || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)));
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchLCDConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setWifiStatus(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                updateChannelSwitchState(WIFISTATUS, apiManager.enableWifi(command.equals(OnOffType.ON)
                        || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)));
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchWifiConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setFtpStatus(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                updateChannelSwitchState(FTPSTATUS, apiManager.enableFtp(command.equals(OnOffType.ON)
                        || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)));
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchFtpConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setAirMediaStatus(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                if (!apiManager.isInLanBridgeMode()) {
                    updateChannelSwitchState(AIRMEDIASTATUS, apiManager.enableAirMedia(command.equals(OnOffType.ON)
                            || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)));
                } else {
                    logger.debug("Thing {}: command {} from channel {} unavailable when in bridge mode",
                            getThing().getUID(), command, channelUID.getId());
                }
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchAirMediaConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setUPnPAVStatus(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                if (!apiManager.isInLanBridgeMode()) {
                    updateChannelSwitchState(UPNPAVSTATUS, apiManager.enableUPnPAV(command.equals(OnOffType.ON)
                            || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)));
                } else {
                    logger.debug("Thing {}: command {} from channel {} unavailable when in bridge mode",
                            getThing().getUID(), command, channelUID.getId());
                }
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchUPnPAVConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setSambaFileStatus(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                updateChannelSwitchState(SAMBAFILESTATUS, apiManager.enableSambaFileShare(command.equals(OnOffType.ON)
                        || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)));
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchSambaConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void setSambaPrinterStatus(ChannelUID channelUID, Command command) {
        if (command instanceof OnOffType || command instanceof OpenClosedType || command instanceof UpDownType) {
            try {
                updateChannelSwitchState(SAMBAPRINTERSTATUS,
                        apiManager.enableSambaPrintShare(command.equals(OnOffType.ON) || command.equals(UpDownType.UP)
                                || command.equals(OpenClosedType.OPEN)));
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
                fetchSambaConfig();
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void reboot(ChannelUID channelUID, Command command) {
        if (command.equals(OnOffType.ON) || command.equals(UpDownType.UP) || command.equals(OpenClosedType.OPEN)) {
            try {
                apiManager.reboot();
            } catch (FreeboxException e) {
                logCommandException(e, channelUID, command);
            }
        } else {
            logger.debug("Thing {}: invalid command {} from channel {}", getThing().getUID(), command,
                    channelUID.getId());
        }
    }

    private void updateChannelStringState(String channel, String state) {
        updateState(new ChannelUID(getThing().getUID(), channel), new StringType(state));
    }

    private void updateChannelSwitchState(String channel, boolean state) {
        updateState(new ChannelUID(getThing().getUID(), channel), state ? OnOffType.ON : OnOffType.OFF);
    }

    private void updateChannelDecimalState(String channel, int state) {
        updateState(new ChannelUID(getThing().getUID(), channel), new DecimalType(state));
    }

    private void updateChannelDecimalState(String channel, long state) {
        updateState(new ChannelUID(getThing().getUID(), channel), new DecimalType(state));
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
    
}