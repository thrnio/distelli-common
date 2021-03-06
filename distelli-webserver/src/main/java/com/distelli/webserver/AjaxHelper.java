/*
  $Id: $
  @file AjaxHelper.java
  @brief Contains the AjaxHelper.java class

  @author Rahul Singh [rsingh]
  Copyright (c) 2013, Distelli Inc., All Rights Reserved.
*/
package com.distelli.webserver;

import com.distelli.webserver.HTTPMethod;
import java.util.Set;
import java.util.HashSet;

public abstract class AjaxHelper<R extends RequestContext>
{
    protected Set<HTTPMethod> supportedHttpMethods = new HashSet<HTTPMethod>();

    public abstract Object get(AjaxRequest ajaxRequest, R requestContext);
    public boolean isMethodSupported(HTTPMethod httpMethod) {
        if(supportedHttpMethods.size() == 0)
            return true;
        return supportedHttpMethods.contains(httpMethod);
    }
}
