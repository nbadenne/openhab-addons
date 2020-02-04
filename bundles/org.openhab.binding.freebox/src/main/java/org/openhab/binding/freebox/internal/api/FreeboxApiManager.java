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
package org.openhab.binding.freebox.internal.api;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;
import org.eclipse.smarthome.io.net.http.HttpUtil;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaReceiver;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaReceiverRequest;
import org.openhab.binding.freebox.internal.api.model.FreeboxAirMediaReceiversResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxAuthorizationStatus;
import org.openhab.binding.freebox.internal.api.model.FreeboxAuthorizationStatusResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxAuthorizeRequest;
import org.openhab.binding.freebox.internal.api.model.FreeboxAuthorizeResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxAuthorizeResult;
import org.openhab.binding.freebox.internal.api.model.FreeboxCallEntry;
import org.openhab.binding.freebox.internal.api.model.FreeboxCallEntryResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxConnectionStatus;
import org.openhab.binding.freebox.internal.api.model.FreeboxConnectionStatusResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxDiscoveryResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxEmptyResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxFtpConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxFtpConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapter;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdapterResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeAdaptersResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNode;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNodeEndpoint;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNodeEndpointResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNodeResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxHomeNodesResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanHost;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanHostResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanHostsResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanInterface;
import org.openhab.binding.freebox.internal.api.model.FreeboxLanInterfacesResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxLcdConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxLcdConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxLoginResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxOpenSessionRequest;
import org.openhab.binding.freebox.internal.api.model.FreeboxOpenSessionResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxOpenSessionResult;
import org.openhab.binding.freebox.internal.api.model.FreeboxPermissions;
import org.openhab.binding.freebox.internal.api.model.FreeboxPhoneStatus;
import org.openhab.binding.freebox.internal.api.model.FreeboxPhoneStatusResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxSambaConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxSambaConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxSystemConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxSystemConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxUPnPAVConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxUPnPAVConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxWifiGlobalConfig;
import org.openhab.binding.freebox.internal.api.model.FreeboxWifiGlobalConfigResponse;
import org.openhab.binding.freebox.internal.api.model.FreeboxXdslStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link FreeboxApiManager} is responsible for the communication with the
 * Freebox. It implements the different HTTP API calls provided by the Freebox
 *
 * @author Laurent Garnier - Initial contribution
 */
public class FreeboxApiManager {

    private final Logger logger = LoggerFactory.getLogger(FreeboxApiManager.class);

    private static final int HTTP_CALL_DEFAULT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(10);
    private static final String AUTH_HEADER = "X-Fbx-App-Auth";
    private static final String HTTP_CALL_CONTENT_TYPE = "application/json; charset=utf-8";

    private String appId;
    private String appName;
    private String appVersion;
    private String deviceName;
    private String baseAddress;
    private String appToken;
    private String sessionToken;
    private Gson gson;
    private FreeboxPermissions freeboxPermissions;

    public FreeboxApiManager(String appId, String appName, String appVersion, String deviceName) {
        this.appId = appId;
        this.appName = appName;
        this.appVersion = appVersion;
        this.deviceName = deviceName;
        this.gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES).create();
    }

    public FreeboxDiscoveryResponse checkApi(String fqdn, boolean secureHttp) {
        String url = String.format("%s://%s/api_version", secureHttp ? "https" : "http", fqdn);
        try {
            String jsonResponse = HttpUtil.executeUrl("GET", url, HTTP_CALL_DEFAULT_TIMEOUT_MS);
            return gson.fromJson(jsonResponse, FreeboxDiscoveryResponse.class);
        } catch (IOException | JsonSyntaxException e) {
            logger.debug("checkApi with {} failed: {}", url, e.getMessage());
            return null;
        }
    }

    public boolean authorize(boolean useHttps, String fqdn, String apiBaseUrl, String apiVersion, String appToken) {
        String[] versionSplit = apiVersion.split("\\.");
        String majorVersion = "5";
        if (versionSplit.length > 0) {
            majorVersion = versionSplit[0];
        }
        this.baseAddress = (useHttps ? "https://" : "http://") + fqdn + apiBaseUrl + "v" + majorVersion + "/";

        boolean granted = false;
        try {
            String token = appToken;
            if (StringUtils.isEmpty(token)) {
                FreeboxAuthorizeRequest request = new FreeboxAuthorizeRequest(appId, appName, appVersion, deviceName);
                FreeboxAuthorizeResult response = executePostUrl("login/authorize/", gson.toJson(request),
                        FreeboxAuthorizeResponse.class, false);
                token = response.getAppToken();
                int trackId = response.getTrackId();

                logger.info("####################################################################");
                logger.info("# Please accept activation request directly on your freebox        #");
                logger.info("# Once done, record Apptoken in the Freebox thing configuration    #");
                logger.info("# {} #", token);
                logger.info("####################################################################");

                FreeboxAuthorizationStatus result;
                do {
                    Thread.sleep(2000);
                    result = executeGetUrl("login/authorize/" + trackId, FreeboxAuthorizationStatusResponse.class,
                            false);
                } while (result.isStatusPending());
                granted = result.isStatusGranted();
            } else {
                granted = true;
            }
            if (!granted) {
                return false;
            }

            this.appToken = token;
            openSession();
            return true;
        } catch (FreeboxException | InterruptedException e) {
            logger.debug("Error while opening a session", e);
            return false;
        }
    }

    private synchronized void openSession() throws FreeboxException {
        if (appToken == null) {
            throw new FreeboxException("No app token to open a new session");
        }
        sessionToken = null;
        String challenge = executeGetUrl("login/", FreeboxLoginResponse.class, false).getChallenge();
        FreeboxOpenSessionRequest request = new FreeboxOpenSessionRequest(appId, hmacSha1(appToken, challenge));
        FreeboxOpenSessionResult freeboxOpenSessionResult = executePostUrl("login/session/", gson.toJson(request),
                FreeboxOpenSessionResponse.class, false,
        false, true);
        freeboxPermissions = freeboxOpenSessionResult.getPermissions();
        sessionToken = freeboxOpenSessionResult.getSessionToken();
    }

    public synchronized void closeSession() {
        if (sessionToken != null) {
            try {
                executePostUrl("login/logout/", null, FreeboxEmptyResponse.class, false);
            } catch (FreeboxException e) {
            }
            sessionToken = null;
        }
    }

    public synchronized String getSessionToken() {
        return sessionToken;
    }

    public FreeboxConnectionStatus getConnectionStatus() throws FreeboxException {
        return executeGetUrl("connection/", FreeboxConnectionStatusResponse.class);
    }

    public String getxDslStatus() throws FreeboxException {
        return executeGetUrl("connection/xdsl/", FreeboxXdslStatusResponse.class).getStatus();
    }

    public boolean isWifiEnabled() throws FreeboxException {
        return executeGetUrl("wifi/config/", FreeboxWifiGlobalConfigResponse.class).isEnabled();
    }

    public boolean enableWifi(boolean enable) throws FreeboxException {
        FreeboxWifiGlobalConfig config = new FreeboxWifiGlobalConfig();
        config.setEnabled(enable);
        return executePutUrl("wifi/config/", gson.toJson(config), FreeboxWifiGlobalConfigResponse.class).isEnabled();
    }

    public boolean isFtpEnabled() throws FreeboxException {
        return executeGetUrl("ftp/config/", FreeboxFtpConfigResponse.class).isEnabled();
    }

    public boolean enableFtp(boolean enable) throws FreeboxException {
        FreeboxFtpConfig config = new FreeboxFtpConfig();
        config.setEnabled(enable);
        return executePutUrl("ftp/config/", gson.toJson(config), FreeboxFtpConfigResponse.class).isEnabled();
    }

    public boolean isAirMediaEnabled() throws FreeboxException {
        return executeGetUrl("airmedia/config/", FreeboxAirMediaConfigResponse.class).isEnabled();
    }

    public boolean enableAirMedia(boolean enable) throws FreeboxException {
        FreeboxAirMediaConfig config = new FreeboxAirMediaConfig();
        config.setEnabled(enable);
        return executePutUrl("airmedia/config/", gson.toJson(config), FreeboxAirMediaConfigResponse.class).isEnabled();
    }

    public boolean isUPnPAVEnabled() throws FreeboxException {
        return executeGetUrl("upnpav/config/", FreeboxUPnPAVConfigResponse.class).isEnabled();
    }

    public boolean enableUPnPAV(boolean enable) throws FreeboxException {
        FreeboxUPnPAVConfig config = new FreeboxUPnPAVConfig();
        config.setEnabled(enable);
        return executePutUrl("upnpav/config/", gson.toJson(config), FreeboxUPnPAVConfigResponse.class).isEnabled();
    }

    public FreeboxSambaConfig getSambaConfig() throws FreeboxException {
        return executeGetUrl("netshare/samba/", FreeboxSambaConfigResponse.class);
    }

    public boolean enableSambaFileShare(boolean enable) throws FreeboxException {
        FreeboxSambaConfig config = new FreeboxSambaConfig();
        config.setFileShareEnabled(enable);
        return executePutUrl("netshare/samba/", gson.toJson(config), FreeboxSambaConfigResponse.class)
                .isFileShareEnabled();
    }

    public boolean enableSambaPrintShare(boolean enable) throws FreeboxException {
        FreeboxSambaConfig config = new FreeboxSambaConfig();
        config.setPrintShareEnabled(enable);
        return executePutUrl("netshare/samba/", gson.toJson(config), FreeboxSambaConfigResponse.class)
                .isPrintShareEnabled();
    }

    public FreeboxLcdConfig getLcdConfig() throws FreeboxException {
        return executeGetUrl("lcd/config/", FreeboxLcdConfigResponse.class);
    }

    public int setLcdBrightness(int brightness) throws FreeboxException {
        FreeboxLcdConfig config = getLcdConfig();
        int newValue = Math.min(100, brightness);
        newValue = Math.max(newValue, 0);
        config.setBrightness(newValue);
        return executePutUrl("lcd/config/", gson.toJson(config), FreeboxLcdConfigResponse.class).getBrightness();
    }

    public int increaseLcdBrightness() throws FreeboxException {
        FreeboxLcdConfig config = getLcdConfig();
        config.setBrightness(Math.min(100, config.getBrightness() + 1));
        return executePutUrl("lcd/config/", gson.toJson(config), FreeboxLcdConfigResponse.class).getBrightness();
    }

    public int decreaseLcdBrightness() throws FreeboxException {
        FreeboxLcdConfig config = getLcdConfig();
        config.setBrightness(Math.max(0, config.getBrightness() - 1));
        return executePutUrl("lcd/config/", gson.toJson(config), FreeboxLcdConfigResponse.class).getBrightness();
    }

    public FreeboxLcdConfig setLcdOrientation(int orientation) throws FreeboxException {
        FreeboxLcdConfig config = getLcdConfig();
        int newValue = Math.min(360, orientation);
        newValue = Math.max(newValue, 0);
        config.setOrientation(newValue);
        config.setOrientationForced(true);
        return executePutUrl("lcd/config/", gson.toJson(config), FreeboxLcdConfigResponse.class);
    }

    public boolean setLcdOrientationForced(boolean forced) throws FreeboxException {
        FreeboxLcdConfig config = getLcdConfig();
        config.setOrientationForced(forced);
        return executePutUrl("lcd/config/", gson.toJson(config), FreeboxLcdConfigResponse.class).isOrientationForced();
    }

    public FreeboxSystemConfig getSystemConfig() throws FreeboxException {
        return executeGetUrl("system/", FreeboxSystemConfigResponse.class);
    }

    public boolean isInLanBridgeMode() throws FreeboxException {
        return executeGetUrl("lan/config/", FreeboxLanConfigResponse.class).isInBridgeMode();
    }

    public List<FreeboxLanHost> getLanHosts() throws FreeboxException {
        List<FreeboxLanHost> hosts = new ArrayList<>();
        List<FreeboxLanInterface> interfaces = executeGetUrl("lan/browser/interfaces/",
                FreeboxLanInterfacesResponse.class);
        if (interfaces != null) {
            for (FreeboxLanInterface lanInterface : interfaces) {
                if (lanInterface.getHostCount() > 0) {
                    List<FreeboxLanHost> lanHosts = getLanHostsFromInterface(lanInterface.getName());
                    if (lanHosts != null) {
                        hosts.addAll(lanHosts);
                    }
                }
            }
        }
        return hosts;
    }

    private List<FreeboxLanHost> getLanHostsFromInterface(String lanInterface) throws FreeboxException {
        return executeGetUrl("lan/browser/" + encodeUrl(lanInterface) + "/", FreeboxLanHostsResponse.class);
    }

    private FreeboxLanHost getLanHostsFromInterface(String lanInterface, String id) throws FreeboxException {
        return executeGetUrl("lan/browser/" + encodeUrl(lanInterface) + "/"+id, FreeboxLanHostResponse.class);
    }

    public FreeboxPhoneStatus getPhoneStatus() throws FreeboxException {
        // This API is undocumented but working
        // It is extracted from the freeboxos-java library
        // https://github.com/MatMaul/freeboxos-java/blob/master/src/org/matmaul/freeboxos/phone/PhoneManager.java#L17
        return executeGetUrl("phone/?_dc=1415032391207", FreeboxPhoneStatusResponse.class).get(0);
    }

    public List<FreeboxCallEntry> getCallEntries() throws FreeboxException {
        return executeGetUrl("call/log/", FreeboxCallEntryResponse.class);
    }

    public List<FreeboxAirMediaReceiver> getAirMediaReceivers() throws FreeboxException {
        return executeGetUrl("airmedia/receivers/", FreeboxAirMediaReceiversResponse.class, true, true, false);
    }

    public void playMedia(String url, String airPlayName, String airPlayPassword) throws FreeboxException {
        FreeboxAirMediaReceiverRequest request = new FreeboxAirMediaReceiverRequest();
        request.setStartAction();
        request.setVideoMediaType();
        if (StringUtils.isNotEmpty(airPlayPassword)) {
            request.setPassword(airPlayPassword);
        }
        request.setMedia(url);
        executePostUrl("airmedia/receivers/" + encodeUrl(airPlayName) + "/", gson.toJson(request),
                FreeboxEmptyResponse.class, true, false, true);
    }

    public List<FreeboxHomeAdapter> getHomeAdapters() throws FreeboxException {
        return executeGetUrl("home/adapters", FreeboxHomeAdaptersResponse.class, true, false, true);
    }

    public FreeboxHomeAdapter getHomeAdapter(int id) throws FreeboxException {
        return executeGetUrl("home/adapters/"+id, FreeboxHomeAdapterResponse.class, true, false, true);
    }

    public List<FreeboxHomeNode> getHomeNodes() throws FreeboxException {
        return executeGetUrl("home/nodes", FreeboxHomeNodesResponse.class, true, false, true);
   }

    public FreeboxHomeNode getHomeNode(int id) throws FreeboxException {
         return executeGetUrl("home/nodes/"+id, FreeboxHomeNodeResponse.class, true, false, true);
    }

    public FreeboxHomeNodeEndpoint getHomeEndpointStatus(int idNode, int idEndpoint) throws FreeboxException {
        return executeGetUrl("home/endpoints/"+idNode+"/"+idEndpoint, FreeboxHomeNodeEndpointResponse.class, true, false, true);
   }

    public void stopMedia(String airPlayName, String airPlayPassword) throws FreeboxException {
        FreeboxAirMediaReceiverRequest request = new FreeboxAirMediaReceiverRequest();
        request.setStopAction();
        request.setVideoMediaType();
        if (StringUtils.isNotEmpty(airPlayPassword)) {
            request.setPassword(airPlayPassword);
        }
        executePostUrl("airmedia/receivers/" + encodeUrl(airPlayName) + "/", gson.toJson(request),
                FreeboxEmptyResponse.class, true, false, true);
    }

    public void reboot() throws FreeboxException {
        executePostUrl("system/reboot/", null, FreeboxEmptyResponse.class);
    }

    private <T extends FreeboxResponse<F>, F> F executeGetUrl(String relativeUrl, Class<T> responseClass)
            throws FreeboxException {
        return executeUrl("GET", relativeUrl, null, responseClass, true, false, false);
    }

    private <T extends FreeboxResponse<F>, F> F executeGetUrl(String relativeUrl, Class<T> responseClass,
            boolean retryAuth) throws FreeboxException {
        return executeUrl("GET", relativeUrl, null, responseClass, retryAuth, false, false);
    }

    private <T extends FreeboxResponse<F>, F> F executeGetUrl(String relativeUrl, Class<T> responseClass,
            boolean retryAuth, boolean patchTableReponse, boolean doNotLogData) throws FreeboxException {
        return executeUrl("GET", relativeUrl, null, responseClass, retryAuth, patchTableReponse, doNotLogData);
    }

    private <T extends FreeboxResponse<F>, F> F executePostUrl(String relativeUrl, String requestContent,
            Class<T> responseClass) throws FreeboxException {
        return executeUrl("POST", relativeUrl, requestContent, responseClass, true, false, false);
    }

    private <T extends FreeboxResponse<F>, F> F executePostUrl(String relativeUrl, String requestContent,
            Class<T> responseClass, boolean retryAuth) throws FreeboxException {
        return executeUrl("POST", relativeUrl, requestContent, responseClass, retryAuth, false, false);
    }

    private <T extends FreeboxResponse<F>, F> F executePostUrl(String relativeUrl, String requestContent,
            Class<T> responseClass, boolean retryAuth, boolean patchTableReponse, boolean doNotLogData)
            throws FreeboxException {
        return executeUrl("POST", relativeUrl, requestContent, responseClass, retryAuth, patchTableReponse,
                doNotLogData);
    }

    private <T extends FreeboxResponse<F>, F> F executePutUrl(String relativeUrl, String requestContent,
            Class<T> responseClass) throws FreeboxException {
        return executeUrl("PUT", relativeUrl, requestContent, responseClass, true, false, false);
    }

    private <T extends FreeboxResponse<F>, F> F executeUrl(String httpMethod, String relativeUrl, String requestContent,
            Class<T> responseClass, boolean retryAuth, boolean patchTableReponse, boolean doNotLogData)
            throws FreeboxException {
        try {
            Properties headers = null;
            String token = getSessionToken();
            if (token != null) {
                headers = new Properties();
                headers.setProperty(AUTH_HEADER, token);
            }
            InputStream stream = null;
            String contentType = null;
            if (requestContent != null) {
                stream = new ByteArrayInputStream(requestContent.getBytes(StandardCharsets.UTF_8));
                contentType = HTTP_CALL_CONTENT_TYPE;
            }
            logger.debug("executeUrl {} {} requestContent {}", httpMethod, relativeUrl,
                    doNotLogData ? "***" : requestContent);
            String jsonResponse = HttpUtil.executeUrl(httpMethod, baseAddress + relativeUrl, headers, stream,
                    contentType, HTTP_CALL_DEFAULT_TIMEOUT_MS);
            if (stream != null) {
                stream.close();
                stream = null;
            }

            if (patchTableReponse) {
                // Replace empty result by an empty table result
                jsonResponse = jsonResponse.replace("\"result\":{}", "\"result\":[]");
            }

            return evaluateJsonReesponse(jsonResponse, responseClass, doNotLogData);
        } catch (FreeboxException e) {
            if (retryAuth && e.isAuthRequired()) {
                logger.debug("Authentication required: open a new session and retry the request");
                openSession();
                return executeUrl(httpMethod, relativeUrl, requestContent, responseClass, false, patchTableReponse,
                        doNotLogData);
            }
            throw e;
        } catch (IOException e) {
            throw new FreeboxException(httpMethod + " request " + relativeUrl + ": execution failed: " + e.getMessage(),
                    e);
        } catch (JsonSyntaxException e) {
            throw new FreeboxException(
                    httpMethod + " request " + relativeUrl + ": response parsing failed: " + e.getMessage(), e);
        }
    }

    private <T extends FreeboxResponse<F>, F> F evaluateJsonReesponse(String jsonResponse, Class<T> responseClass,
            boolean doNotLogData) throws JsonSyntaxException, FreeboxException {
        logger.debug("evaluateJsonReesponse Json {}", doNotLogData ? "***" : jsonResponse);
        // First check only if the result is successful
        FreeboxResponse<Object> partialResponse = gson.fromJson(jsonResponse, FreeboxEmptyResponse.class);
        partialResponse.evaluate();
        // Parse the full response in case of success
        T fullResponse = gson.fromJson(jsonResponse, responseClass);
        fullResponse.evaluate();
        F result = fullResponse.getResult();
        return result;
    }

    private String encodeUrl(String url) throws FreeboxException {
        try {
            return URLEncoder.encode(url, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            throw new FreeboxException("Encoding the URL \"" + url + "\" in UTF-8 failed", e);
        }
    }

    private static String hmacSha1(String key, String value) throws FreeboxException {
        try {
            // Get an hmac_sha1 key from the raw key bytes
            byte[] keyBytes = key.getBytes();
            SecretKeySpec signingKey = new SecretKeySpec(keyBytes, "HmacSHA1");

            // Get an hmac_sha1 Mac instance and initialize with the signing key
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signingKey);

            // Compute the hmac on input data bytes
            byte[] rawHmac = mac.doFinal(value.getBytes());

            // Convert raw bytes to Hex
            byte[] hexBytes = new Hex().encode(rawHmac);

            // Covert array of Hex bytes to a String
            return new String(hexBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | NoSuchAlgorithmException | InvalidKeyException | IllegalStateException e) {
            throw new FreeboxException("Computing the hmac-sha1 of the challenge and the app token failed", e);
        }
    }

    public FreeboxPermissions getFreeboxPermissions() {
        return freeboxPermissions;
    }

}
