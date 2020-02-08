package org.openhab.binding.freebox.internal.handler;

import static org.openhab.binding.freebox.internal.FreeboxBindingConstants.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.library.types.DateTimeType;
import org.eclipse.smarthome.core.library.types.DecimalType;
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
import org.openhab.binding.freebox.internal.api.model.FreeboxCallEntry;
import org.openhab.binding.freebox.internal.api.model.FreeboxPhoneStatus;
import org.openhab.binding.freebox.internal.config.FreeboxPhoneConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FreeboxPhoneHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(FreeboxPhoneHandler.class);

    private FreeboxHandler freeboxHandler;

    private ScheduledFuture<?> phoneJob;
    private ScheduledFuture<?> callsJob;

    private FreeboxPhoneConfiguration config;

    public FreeboxPhoneHandler(Thing thing) {
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
        if (phoneJob != null) {
            phoneJob.cancel(true);
            phoneJob = null;
        }
        if (callsJob != null) {
            callsJob.cancel(true);
            callsJob = null;
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

        config = getConfigAs(FreeboxPhoneConfiguration.class);
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
        if (phoneJob == null || phoneJob.isCancelled()) {
            phoneJob = scheduler.scheduleWithFixedDelay(this::pollPhoneState, 1, getConfigAs(FreeboxPhoneConfiguration.class).refreshPhoneInterval, TimeUnit.SECONDS);
        }
        if (callsJob == null || callsJob.isCancelled()) {
            callsJob = scheduler.scheduleWithFixedDelay(this::pollPhoneCalls, 1, getConfigAs(FreeboxPhoneConfiguration.class).refreshPhoneCallsInterval, TimeUnit.SECONDS);
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

    private void pollPhoneState() {
        logger.debug("Polling phone state...");
        try {
            FreeboxPhoneStatus phoneStatus = freeboxHandler.getApiManager().getPhoneStatus();
            updateGroupChannelSwitchState(STATE, ONHOOK, phoneStatus.isOnHook());
            updateGroupChannelSwitchState(STATE, RINGING, phoneStatus.isRinging());
            updateStatus(ThingStatus.ONLINE);
        } catch (FreeboxException e) {
            if (e.isMissingRights()) {
                logger.debug("Phone state job: missing right {}", e.getResponse().getMissingRight());
                updateStatus(ThingStatus.ONLINE);
            } else {
                logger.debug("Phone state job failed: {}", e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    private void pollPhoneCalls() {
        logger.debug("Polling phone calls...");
        try {
            //List<FreeboxCallEntry> callEntries = freeboxHandler.getApiManager().getCallEntries();
            List<FreeboxCallEntry> callEntriesAccepted = freeboxHandler.getApiManager().getCallEntriesByType(ACCEPTED);
            List<FreeboxCallEntry> callEntriesMissed = freeboxHandler.getApiManager().getCallEntriesByType(MISSED);
            List<FreeboxCallEntry> callEntriesOutgoing = freeboxHandler.getApiManager().getCallEntriesByType(OUTGOING);
            List<FreeboxCallEntry> lastEntries = new ArrayList<FreeboxCallEntry>();
            PhoneCallComparator comparator = new PhoneCallComparator();
            if (callEntriesAccepted != null && !callEntriesAccepted.isEmpty()) {
                Collections.sort(callEntriesAccepted, comparator);
                lastEntries.add(callEntriesAccepted.get(0));
                updateCall(callEntriesAccepted.get(0), ACCEPTED);
            }
            if (callEntriesMissed != null && !callEntriesMissed.isEmpty()) {
                Collections.sort(callEntriesMissed, comparator);
                lastEntries.add(callEntriesMissed.get(0));
                updateCall(callEntriesMissed.get(0), MISSED);
            }
            if (callEntriesOutgoing != null && !callEntriesOutgoing.isEmpty()) {
                Collections.sort(callEntriesOutgoing, comparator);
                lastEntries.add(callEntriesOutgoing.get(0));
                updateCall(callEntriesOutgoing.get(0), OUTGOING);
            }
            if (!lastEntries.isEmpty()) {
                Collections.sort(lastEntries, comparator);
                updateCall(lastEntries.get(0), ANY);
            }
            updateStatus(ThingStatus.ONLINE);
        } catch (FreeboxException e) {
            if (e.isMissingRights()) {
                logger.debug("Phone calls job: missing right {}", e.getResponse().getMissingRight());
                updateStatus(ThingStatus.ONLINE);
            } else {
                logger.debug("Phone calls job failed: {}", e.getMessage(), e);
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, e.getMessage());
            }
        }
    }

    private void updateCall(FreeboxCallEntry call, String channelGroup) {
        if (channelGroup != null) {
            updateGroupChannelStringState(channelGroup, CALLNUMBER, call.getNumber());
            updateGroupChannelDecimalState(channelGroup, CALLDURATION, call.getDuration());
            ZonedDateTime zoned = ZonedDateTime.ofInstant(Instant.ofEpochMilli(call.getTimeStamp().getTimeInMillis()),
                    TimeZone.getDefault().toZoneId());
            updateGroupChannelDateTimeState(channelGroup, CALLTIMESTAMP, zoned);
            updateGroupChannelStringState(channelGroup, CALLNAME, call.getName());
            if (channelGroup.equals(ANY)) {
                updateGroupChannelStringState(channelGroup, CALLSTATUS, call.getType());
            }
        }
    }

    private void updateGroupChannelSwitchState(String group, String channel, boolean state) {
        updateState(new ChannelUID(getThing().getUID(), group, channel), state ? OnOffType.ON : OnOffType.OFF);
    }

    private void updateGroupChannelStringState(String group, String channel, String state) {
        updateState(new ChannelUID(getThing().getUID(), group, channel), new StringType(state));
    }

    private void updateGroupChannelDecimalState(String group, String channel, int state) {
        updateState(new ChannelUID(getThing().getUID(), group, channel), new DecimalType(state));
    }

    private void updateGroupChannelDateTimeState(String group, String channel, ZonedDateTime zonedDateTime) {
        updateState(new ChannelUID(getThing().getUID(), group, channel), new DateTimeType(zonedDateTime));
    }

    /**
     * A comparator of phone calls by ascending end date and time
     */
    private class PhoneCallComparator implements Comparator<FreeboxCallEntry> {

        @Override
        public int compare(FreeboxCallEntry call1, FreeboxCallEntry call2) {
            int result = 0;
            Calendar callEndTime1 = call1.getTimeStamp();
            callEndTime1.add(Calendar.SECOND, call1.getDuration());
            Calendar callEndTime2 = call2.getTimeStamp();
            callEndTime2.add(Calendar.SECOND, call2.getDuration());
            if (callEndTime1.before(callEndTime2)) {
                result = 1;
            } else if (callEndTime1.after(callEndTime2)) {
                result = -1;
            }
            return result;
        }

    }
    
}