/*
  $Id: $
  @file AjaxRequest.java
  @brief Contains the AjaxRequest.java class

  @author Rahul Singh [rsingh]
*/
package com.distelli.webserver;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

import com.distelli.webserver.JsonError;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.core.JsonProcessingException;

public class AjaxRequest
{
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    static {
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    protected String operation = null;
    protected Map<String, String> params = null;
    protected JsonNode content = null;

    public AjaxRequest()
    {

    }

    public void setContent(JsonNode content)
    {
        this.content = content;
    }

    public JsonNode getContent()
    {
        return this.content;
    }

    public JsonNode getContent(String jsonPointer)
    {
        return getContent(jsonPointer, false);
    }

    public JsonNode getContent(String jsonPointer, boolean throwIfNull)
    {
        if(this.content != null)
        {
            JsonNode dataNode = this.content.at(jsonPointer);
            if(!dataNode.isMissingNode())
                return dataNode;
        }
        if(throwIfNull)
            throw(new AjaxClientException(JsonError.BadContent));
        return null;
    }

    public String getContentAsString(String jsonPointer)
    {
        return getContentAsString(jsonPointer, false);
    }

    public String getContentAsString(String jsonPointer, boolean throwIfNull)
    {
        try {
            JsonNode jsonNode = getContent(jsonPointer, throwIfNull);
            return OBJECT_MAPPER.writeValueAsString(jsonNode);
        } catch(JsonProcessingException jpe) {
            throw(new AjaxClientException("Invalid json in content field: "+jsonPointer, JsonError.Codes.BadContent, 400));
        }
    }

    public <T> T convertContent(Class<T> clazz)
    {
        return convertContent(clazz, false);
    }

    public <T> T convertContent(Class<T> clazz, boolean throwIfNull)
    {
        if(this.content != null)
        {
            T contentObj = OBJECT_MAPPER.convertValue(this.content, clazz);
            if(contentObj != null)
                return contentObj;
        }
        if(throwIfNull)
            throw(new AjaxClientException(JsonError.BadContent));
        return null;
    }

    public <T> T convertContent(String jsonPointer, Class<T> clazz, boolean throwIfNull)
    {
        T contentObj = null;
        if(this.content != null)
        {
            JsonNode dataNode = this.content.at(jsonPointer);
            if(!dataNode.isMissingNode())
                contentObj= OBJECT_MAPPER.convertValue(dataNode, clazz);
            if(contentObj != null)
                return contentObj;
        }
        if(throwIfNull)
            throw(new AjaxClientException(JsonError.BadContent));
        return null;
    }

    public void setOperation(String operation)
    {
        this.operation = operation;
    }

    public String getOperation()
    {
        return this.operation;
    }

    public void setParams(Map<String, String> params)
    {
        this.params = params;
    }

    public Map<String, String> getParams()
    {
        return this.params;
    }

    public String getParam(String key)
    {
        return getParam(key, false);
    }

    public int getParamAsInt(String key, int defaultValue)
    {
        String param = getParam(key);
        if(param == null)
            return defaultValue;
        try {
            return Integer.parseInt(param);
        } catch(NumberFormatException nfe) {
            throw(new AjaxClientException("Invalid value for param '"+key+"' in request",
                                          JsonError.Codes.BadParam,
                                          400));
        }
    }


    public String getParam(String key, boolean throwIfMissing)
    {
        String retVal = null;
        if(this.params != null)
            retVal = this.params.get(key);
        if(retVal != null)
            return retVal;
        if(throwIfMissing)
            throw(new AjaxClientException("Missing Param '"+key+"' in request",
                                          JsonError.Codes.MissingParam,
                                          400));
        return null;
    }

    public <T extends Enum<T>> T getParamAsEnum(String key, Class<T> type)
    {
        return getParamAsEnum(key, type, false);
    }

    public <T extends Enum<T>> T getParamAsEnum(String key, Class<T> type, boolean throwIfMissing)
        throws AjaxClientException
    {
        if(this.params == null)
            return null;
        String value = this.params.get(key);
        T retVal = null;
        try {
            if(value != null)
                retVal = Enum.valueOf(type, value);
        } catch(IllegalArgumentException iae) {
            retVal = null;
        }

        if(retVal != null)
            return retVal;

        if(throwIfMissing)
            throw(new AjaxClientException("Missing Param '"+key+"' in request",
                                          JsonError.Codes.MissingParam,
                                          400));
        return null;
    }

    public static AjaxRequest fromJson(JsonNode node)
    {
        AjaxRequest ajaxRequest = new AjaxRequest();
        String operation = node.at("/op").asText();
        if(operation != null && !operation.trim().isEmpty())
            ajaxRequest.setOperation(operation);
        JsonNode paramsNode = node.at("/params");
        if(!paramsNode.isMissingNode())
        {
            TypeReference<HashMap<String, String>> typeRef = new TypeReference<HashMap<String,String>>(){};
            HashMap<String, String> params = OBJECT_MAPPER.convertValue(paramsNode, typeRef);

            ajaxRequest.setParams(params);
        }
        JsonNode contentNode = node.at("/content");
        if(!contentNode.isMissingNode())
            ajaxRequest.setContent(contentNode);
        return ajaxRequest;
    }

    public static AjaxRequest fromQueryParams(Map<String, List<String>> queryParams)
    {
        AjaxRequest ajaxRequest = new AjaxRequest();
        String operation = getParam("op", queryParams);
        if(operation != null && !operation.trim().isEmpty())
            ajaxRequest.setOperation(operation);

        Map<String, String> params = null;
        for(String param : queryParams.keySet())
        {
            if(param.equalsIgnoreCase("op"))
                continue;
            String value = getParam(param, queryParams);
            if(value != null)
            {
                if(params == null)
                    params = new HashMap<String, String>();
                params.put(param, value);
            }
        }
        ajaxRequest.setParams(params);
        return ajaxRequest;
    }

    private static String getParam(String key, Map<String, List<String>> params)
    {
        if(params == null)
            return null;
        List<String> values = params.get(key);
        if(values == null || values.size() == 0)
            return null;
        return values.get(0);
    }

    @Override
    public String toString() {
        return String.format("AjaxRequest[content=%s, op=%s, params=%s]", content, operation, params);
    }
}
