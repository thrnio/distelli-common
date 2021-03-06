package com.distelli.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.*;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RequestContext
{
    /**
       @brief Logger used for error logging
    */
    private static final Logger log = LoggerFactory.getLogger(RequestContext.class);

    private static final ObjectMapper _objectMapper = new ObjectMapper();

    protected Map<String, String> _headers = null;
    protected Map<String, List<String>> _queryParams = null;
    protected MatchedRoute _matchedRoute;
    protected HTTPMethod _httpMethod = null;
    protected String _scheme = null;
    protected String _path = null;
    protected String _originalPath = null;
    protected String _queryString = null;
    protected String _contentType = null;
    protected String _remoteAddress = null;
    protected String _remoteUser = null;
    protected String _requestId = null;
    protected int _contentLength = 0;
    protected InputStream _requestStream = null;
    protected JsonNode _jsonContent = null;
    protected Cookie _sessionCookie = null;
    protected Cookie[] _inputCookies = null;
    protected boolean _unmarshallJson;
    protected WebSession _webSession = null;

    public RequestContext(HTTPMethod httpMethod, HttpServletRequest request) {
        this(httpMethod, request, true);
    }

    public RequestContext(HTTPMethod httpMethod, HttpServletRequest request, boolean unmarshallJson)
    {
        _unmarshallJson = unmarshallJson;
        _headers = extractHeaders(request);
        loadQueryParams(request.getParameterMap());
        _httpMethod = httpMethod;

        _scheme = request.getScheme();

        _originalPath = request.getRequestURI();
        _path = _originalPath;
        // clean off trailing slashes
        if(_path != null) {
            while (_path.endsWith("/") && _path.length() > 1)
                _path = _path.substring(0, _path.length() - 1);
        }

        _queryString = request.getQueryString();
        _contentLength = request.getContentLength();
        _remoteAddress = request.getRemoteAddr();
        _remoteUser = request.getRemoteUser();
        _contentLength = request.getContentLength();
        _contentType = parseContentType(request);
        _inputCookies = request.getCookies();
        _requestId = _headers.get(WebConstants.REQUEST_ID_HEADER);
        if(_requestId == null)
            _requestId = UUID.randomUUID().toString();
        if(!unmarshallJson(request))
        {
            try {
                _requestStream = request.getInputStream();
            } catch(IOException ioe) {
                throw(new WebServerException(ioe));
            }
        }
    }

    public String toString() {
        // Basic description of this request context.
        StringBuilder sb = new StringBuilder();
        sb.append(getHttpMethod());
        sb.append(" ");
        sb.append(getProto());
        sb.append("://");
        sb.append(getHostPort("UNKNOWN"));
        sb.append(getPath());
        sb.append("?");
        if ( null == getQueryParams() ) {
            sb.append("<null>");
        } else {
            boolean isFirst = true;
            for ( Map.Entry<String, List<String>> pair : getQueryParams().entrySet() ) {
                for ( String value : pair.getValue() ) {
                    if ( ! isFirst ) sb.append("&");
                    isFirst = false;
                    sb.append(URLEncoder.encode(pair.getKey()));
                    sb.append("=");
                    sb.append(URLEncoder.encode(value));
                }
            }
        }
        return sb.toString();
    }

    private String parseContentType(HttpServletRequest request)
    {
        String contentType = request.getContentType();
        if(contentType == null)
            return null;

        contentType = contentType.trim();

        String[] parts = contentType.split(";");
        if(parts == null)
            return null;
        if(parts.length > 1)
            return parts[0];
        return contentType;
    }

    private void loadQueryParams(Map<String, String[]> params)
    {
        if ( null == params ) return;
        _queryParams = new TreeMap<String, List<String>>();
        for(Map.Entry<String, String[]> param : params.entrySet())
        {
            String paramKey = param.getKey();
            List<String> paramValues = _queryParams.get(paramKey);
            if(paramValues == null)
            {
                paramValues = new ArrayList<String>();
                _queryParams.put(paramKey, paramValues);
            }

            String[] newValues = param.getValue();
            for(String newValue : newValues)
                paramValues.add(newValue);
        }
    }

    public String getContentType()
    {
        return _contentType;
    }

    private boolean unmarshallJson(HttpServletRequest request)
    {
        if(!_unmarshallJson) return false;
        try
        {
            if(_contentType != null && _contentType.equalsIgnoreCase(WebConstants.CONTENT_TYPE_JSON))
            {
                if(_httpMethod != null && (_httpMethod == HTTPMethod.POST || _httpMethod == HTTPMethod.PUT))
                {
                    _jsonContent = _objectMapper.readTree(request.getInputStream());
                    return true;
                }
            }
            return false;
        }
        catch(JsonProcessingException jpe)
        {
            if(log.isDebugEnabled())
                log.debug("Caught JsonProcessingException: "+jpe.getMessage(), jpe);
            throw(new WebClientException("Invalid JSON in request"));
        }
        catch(IOException ioe)
        {
            throw(new WebServerException());
        }
    }

    private Map<String, String> extractHeaders(HttpServletRequest request)
    {
        Map<String, String> headers = new TreeMap<String, String>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if(headerNames == null)
            return headers;

        while(headerNames.hasMoreElements())
        {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            headers.put(headerName.toLowerCase(), headerValue);
        }

        return headers;
    }

    public String getQueryString()
    {
        return _queryString;
    }

    public String getProto() {
        String proto = getHeaderValue("x-forwarded-proto");
        if ( null == proto ) proto = _scheme;
        if ( null == proto ) return "http";
        return proto;
    }

    public String getHostPort(String defaultValue) {
        String hostPort = getHeaderValue("host");
        if ( null == hostPort || hostPort.length() == 0 ) return defaultValue;
        return hostPort;
    }

    public String getHost(String defaultValue) {
        String hostPort = getHeaderValue("host");
        if ( null == hostPort || hostPort.length() == 0 ) return defaultValue;
        int colon = hostPort.lastIndexOf(':');
        if ( colon > 0 ) {
            return hostPort.substring(0, colon);
        }
        return hostPort;
    }
    public int getPort() {
        String hostPort = getHeaderValue("host");
        if ( null != hostPort ) {
            int colon = hostPort.lastIndexOf(':');
            if ( colon > 0 ) {
                try {
                    return Integer.parseInt(hostPort.substring(colon+1));
                } catch ( NumberFormatException ex ) {
                    // ignore it.
                }
            }
        }
        String proto = getProto().toLowerCase();
        if ( "http".equals(proto) ) return 80;
        if ( "https".equals(proto) ) return 443;
        // Unknown protocol... really we should read /etc/services.
        return -1;
    }
    public final void setHttpMethod(HTTPMethod httpMethod)
    {
        _httpMethod = httpMethod;
    }

    public final HTTPMethod getHttpMethod()
    {
        return _httpMethod;
    }

    public final int getContentLength() {
        return this._contentLength;
    }

    public final Map<String,String> getHeaders() {
        return this._headers;
    }

    public String getHeaderValue(String key)
    {
        if(key == null)
            return null;
        if(_headers == null)
            return null;

        return _headers.get(key.toLowerCase());
    }

    public void addHeader(String key, String value) {
        _headers.put(key, value);
    }

    public final Map<String,List<String>> getQueryParams() {
        return this._queryParams;
    }

    /**
       This method returns the value for a queryString or POST data
       parameter. If there are multiple values for the same key then
       this will return the first one. To get all of them use
       getParameters(...)
     */
    public final String getParameter(String key)
    {
        List<String> params = _queryParams.get(key);
        if(params == null)
            return null;
        if(params.size() > 0)
            return params.get(0);
        return null;
    }

    public final List<String> getParameters(String key)
    {
        return _queryParams.get(key);
    }

    public final void addQueryParam(String key, String value) {
        List<String> values = _queryParams.get(key);
        if(values == null)
        {
            values = new ArrayList<String>();
            _queryParams.put(key, values);
        }

        values.add(value);
    }

    public final String getPath() {
        return this._path;
    }

    public final void setPath(String path) {
        _path = path;
    }

    public String getOriginalPath() {
        return _originalPath;
    }

    public final void setRemoteAddress(String remoteAddress) {
        _remoteAddress = remoteAddress;
    }

    public final String getRemoteAddress() {
        return this._remoteAddress;
    }

    public final void setRemoteUser(String remoteUser) {
        _remoteUser = remoteUser;
    }

    public final String getRemoteUser() {
        return this._remoteUser;
    }

    public final String getRequestId() {
        return this._requestId;
    }

    public boolean isGZipAccepted()
    {
        String acceptEncodingHeader = _headers.get(WebConstants.ACCEPT_ENCODING_HEADER);
        if(acceptEncodingHeader == null)
            return false;
        String[] acceptedValues = acceptEncodingHeader.split(",");
        if(acceptedValues == null || acceptedValues.length == 0)
            return false;
        for(String acceptedValue : acceptedValues)
        {
            String[] acceptedValueParts = acceptedValue.split(";");
            if(acceptedValueParts.length == 0)
                continue;
            String acceptedEncoding = acceptedValueParts[0].trim();
            if(acceptedEncoding.equalsIgnoreCase("gzip") || acceptedEncoding.equalsIgnoreCase("*"))
                return true;
        }
        return false;
    }

    public InputStream getRequestStream()
    {
        return _requestStream;
    }

    public JsonNode getJsonContent()
    {
        return _jsonContent;
    }

    public Cookie getSessionCookie()
    {
        return _sessionCookie;
    }

    public void setSessionCookie(Cookie sessionCookie)
    {
        _sessionCookie = sessionCookie;
    }

    public Cookie[] getCookies() {
        return _inputCookies;
    }

    public void setMatchedRoute(MatchedRoute matchedRoute)
    {
        _matchedRoute = matchedRoute;
    }

    public MatchedRoute getMatchedRoute()
    {
        return _matchedRoute;
    }

    public WebSession getWebSession() {
        return _webSession;
    }

    public void setWebSession(WebSession webSession) {
        _webSession = webSession;
    }
}
