/*******************************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *******************************************************************************/
package org.ofbiz.common;

import java.io.IOException;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.ofbiz.base.util.Debug;
import org.ofbiz.base.util.StringUtil;
import org.ofbiz.base.util.UtilValidate;
import org.ofbiz.entity.Delegator;
import org.ofbiz.entity.DelegatorFactory;
import org.ofbiz.entity.GenericEntityException;
import org.ofbiz.entity.GenericValue;
import org.ofbiz.entity.util.EntityQuery;
import org.ofbiz.entity.util.EntityUtil;
import org.ofbiz.service.TicketUtil;
import org.ofbiz.webapp.control.ContextFilter;
import org.ofbiz.webapp.website.WebSiteWorker;

public class UrlServletHelper extends ContextFilter {

    public final static String module = UrlServletHelper.class.getName();

    public static void setRequestAttributes(ServletRequest request, Delegator delegator, ServletContext servletContext) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        // check if multi tenant is enabled
        boolean useMultitenant = EntityUtil.isMultiTenantEnabled();

        // 加个Tenant的判断
        if (useMultitenant) {
            String ticket = TicketUtil.getTicketFormRequest(request);
            if (ticket == null) {
                Debug.logWarning(null, "Unable to get Tenant", module);
            } else {
                delegator.setTenantId(TicketUtil.getTenantFromTicket(ticket));
            }
        }

        // set the web context in the request for future use
        request.setAttribute("servletContext", httpRequest.getSession().getServletContext());
        request.setAttribute("delegator", delegator);

    }

    public static void setViewQueryParameters(ServletRequest request, StringBuilder urlBuilder) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (UtilValidate.isEmpty(httpRequest.getServletPath())) {
            return;
        }
        String pathInfo = httpRequest.getServletPath();
        String viewIndex = null;
        String viewSize = null;
        String viewSort = null;
        String searchString = null;

        int queryStringIndex = pathInfo.indexOf("?");
        if (queryStringIndex >= 0) {
            List<String> queryStringTokens = StringUtil.split(pathInfo.substring(queryStringIndex + 1), "&");
            for (String queryStringToken : queryStringTokens) {
                int equalIndex = queryStringToken.indexOf("=");
                String name = queryStringToken.substring(0, equalIndex - 1);
                String value = queryStringToken.substring(equalIndex + 1, queryStringToken.length() - 1);

                if ("viewIndex".equals(name)) {
                    viewIndex = value;
                } else if ("viewSize".equals(name)) {
                    viewSize = value;
                } else if ("viewSort".equals(name)) {
                    viewSort = value;
                } else if ("searchString".equals(name)) {
                    searchString = value;
                }
            }
        }

        if (UtilValidate.isNotEmpty(httpRequest.getParameter("viewIndex"))) {
            viewIndex = httpRequest.getParameter("viewIndex");
        }
        if (UtilValidate.isNotEmpty(httpRequest.getParameter("viewSize"))) {
            viewSize = httpRequest.getParameter("viewSize");
        }
        if (UtilValidate.isNotEmpty(httpRequest.getParameter("viewSort"))) {
            viewSort = httpRequest.getParameter("viewSort");
        }
        if (UtilValidate.isNotEmpty(httpRequest.getParameter("searchString"))) {
            searchString = httpRequest.getParameter("searchString");
        }

        //Set query string parameters to url
        if (UtilValidate.isNotEmpty(viewIndex)) {
            urlBuilder.append("/~VIEW_INDEX=" + viewIndex);
            request.setAttribute("VIEW_INDEX", viewIndex);
        }
        if (UtilValidate.isNotEmpty(viewSize)) {
            urlBuilder.append("/~VIEW_SIZE=" + viewSize);
            request.setAttribute("VIEW_SIZE", viewSize);
        }
        if (UtilValidate.isNotEmpty(viewSort)) {
            urlBuilder.append("/~VIEW_SORT=" + viewSort);
            request.setAttribute("VIEW_SORT", viewSort);
        }
        if (UtilValidate.isNotEmpty(searchString)) {
            urlBuilder.append("/~SEARCH_STRING=" + searchString);
            request.setAttribute("SEARCH_STRING", searchString);
        }
    }

    public static void checkPathAlias(ServletRequest request, ServletResponse response, Delegator delegator, String pathInfo) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String webSiteId = WebSiteWorker.getWebSiteId(request);
        // check path alias
        GenericValue pathAlias = null;
        try {
            pathAlias = EntityQuery.use(delegator)
                    .from("WebSitePathAlias")
                    .where("webSiteId", webSiteId, "pathAlias", pathInfo)
                    .cache()
                    .queryOne();
        } catch (GenericEntityException e) {
            Debug.logError(e, module);
        }
        if (pathAlias != null) {
            String alias = pathAlias.getString("aliasTo");
            String contentId = pathAlias.getString("contentId");
            if (contentId == null && UtilValidate.isNotEmpty(alias)) {
                if (!alias.startsWith("/")) {
                    alias = "/" + alias;
                }

                RequestDispatcher rd = request.getRequestDispatcher(alias);
                try {
                    rd.forward(request, response);
                    return;
                } catch (ServletException e) {
                    Debug.logWarning(e, module);
                } catch (IOException e) {
                    Debug.logWarning(e, module);
                }
            }
        } else {
            // send 404 error if a URI is alias TO
            try {
                List<GenericValue> aliasTos = EntityQuery.use(delegator)
                        .from("WebSitePathAlias")
                        .where("webSiteId", webSiteId, "aliasTo", httpRequest.getRequestURI())
                        .queryList();
                if (UtilValidate.isNotEmpty(aliasTos)) {
                    httpResponse.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
                    return;
                }
            } catch (GenericEntityException e) {
                Debug.logError(e, module);
            } catch (IOException e) {
                Debug.logError(e, module);
            }
        }
    }

    public static String invalidCharacter(String str) {
        str = str.replace("&", "-");
        str = str.replace("\"", "-");
        str = str.replace("×", "-");
        str = str.replace("÷", "-");
        str = str.replace(" ", "-");
        str = str.replace("!", "-");
        str = str.replace("#", "-");
        str = str.replace("$", "-");
        str = str.replace("%", "-");
        str = str.replace("'", "-");
        str = str.replace("(", "-");
        str = str.replace(")", "-");
        str = str.replace("*", "-");
        str = str.replace("+", "-");
        str = str.replace(",", "-");
        str = str.replace(".", "-");
        str = str.replace("/", "-");
        str = str.replace(":", "-");
        str = str.replace(";", "-");
        str = str.replace("<", "-");
        str = str.replace("=", "-");
        str = str.replace(">", "-");
        str = str.replace("?", "-");
        str = str.replace("@", "-");
        str = str.replace("[", "-");
        str = str.replace("\\", "-");
        str = str.replace("]", "-");
        str = str.replace("^", "-");
        str = str.replace("_", "-");
        str = str.replace("`", "-");
        str = str.replace("{", "-");
        str = str.replace("|", "-");
        str = str.replace("}", "-");
        str = str.replace("~", "-");
        str = str.replace("￠", "-");
        str = str.replace("￡", "-");
        str = str.replace("¤", "-");
        str = str.replace("§", "-");
        str = str.replace("¨", "-");
        str = str.replace("¬", "-");
        str = str.replace("ˉ", "-");
        str = str.replace("°", "-");
        str = str.replace("±", "-");
        str = str.replace("μ", "-");
        str = str.replace("•", "-");
        str = str.replace("！", "-");
        str = str.replace("￥", "-");
        str = str.replace("……", "-");
        str = str.replace("（", "-");
        str = str.replace("）", "-");
        str = str.replace("——", "-");
        str = str.replace("【", "-");
        str = str.replace("】", "-");
        str = str.replace("｛", "-");
        str = str.replace("｝", "-");
        str = str.replace("：", "-");
        str = str.replace("；", "-");
        str = str.replace("“", "-");
        str = str.replace("、", "-");
        str = str.replace("《", "-");
        str = str.replace("》", "-");
        str = str.replace("，", "-");
        str = str.replace("。", "-");
        str = str.replace("‘", "-");
        str = str.replace("？", "-");
        str = str.replace("–", "");
        while (str.startsWith("-")) {
            str = str.substring(1);
        }
        while (str.endsWith("-")) {
            str = str.substring(0, str.length() - 1);
        }
        while (str.indexOf("--") != -1) {
            str = str.replace("--", "-");
        }
        return str;
    }
}

