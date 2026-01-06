package com.qiyi.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONWriter;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.options.RequestOptions;
import com.qiyi.util.DingTalkUtil;
import com.qiyi.util.PlayWrightUtil;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.locks.ReentrantLock;

public class QueryErpOrderTool implements Tool {
    private static final ReentrantLock TOOL_LOCK = new ReentrantLock();
    private static final String ERP_ORDER_PAGE_URL = "https://sc.scm121.com/tradeManage/tower/distribute";
    private static final String API_URL = "https://innerapi.scm121.com/api/inner/order/list";
    private final Map<String, String> capturedApiHeaders = new HashMap<>();

    @Override
    public String getName() {
        return "query_erp_order";
    }

    @Override
    public String getDescription() {
        return "Query ERP order list. Checks login status on ERP page first, notifies if login is needed, then fetches data. Parameters: orderId (string, mandatory).";
    }

    @Override
    public String execute(JSONObject params, String senderId, List<String> atUserIds) {
        List<String> notifyUsers = new ArrayList<>();
        if (senderId != null) notifyUsers.add(senderId);
        if (atUserIds != null && !atUserIds.isEmpty()) {
            notifyUsers.addAll(atUserIds);
        }

        String orderId = params != null ? params.getString("orderId") : null;
        if (orderId == null || orderId.isEmpty()) {
            try {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "ERP订单查询缺少必填参数：订单号 (orderId)");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Error: Missing orderId";
        }

        if (!TOOL_LOCK.tryLock()) {
            try {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "ERP查询任务正在执行中，请稍后再试。");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return "Error: Task locked";
        }

        PlayWrightUtil.Connection connection = null;
        try {
            DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "开始执行ERP订单查询任务...");

            connection = PlayWrightUtil.connectAndAutomate();
            if (connection == null) {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "无法连接到浏览器，任务终止");
                return "Error: Browser connection failed";
            }

            BrowserContext context;
            if (connection.browser.contexts().isEmpty()) {
                context = connection.browser.newContext();
            } else {
                context = connection.browser.contexts().get(0);
            }

            Page page = context.newPage();
            try {
                // 1. Check Login
                if (!checkLogin(page, notifyUsers)) {
                    return "Error: Login failed";
                }

                // 2. Fetch Data
                return fetchData(page, notifyUsers, orderId);

            } finally {
                page.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
            try {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "任务执行异常: " + e.getMessage());
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return "Error: " + e.getMessage();
        } finally {
            if (connection != null) {
                PlayWrightUtil.disconnectBrowser(connection.playwright, connection.browser);
            }
            TOOL_LOCK.unlock();
        }
    }

    private boolean checkLogin(Page page, List<String> notifyUsers) {
        try {
            // Setup network interception to capture headers from real requests
            capturedApiHeaders.clear();
            page.onRequest(request -> {
                String url = request.url();
                if (url.contains("innerapi.scm121.com") && !url.contains(".js") && !url.contains(".css")) {
                    Map<String, String> headers = request.headers();
                    // Store headers if we haven't already, or overwrite
                    // We prefer headers from POST requests as they are likely API calls
                    if (request.method().equalsIgnoreCase("POST") || capturedApiHeaders.isEmpty()) {
                        capturedApiHeaders.putAll(headers);
                        // Remove content-specific headers that we will regenerate
                        capturedApiHeaders.remove("content-length");
                        capturedApiHeaders.remove("content-type");
                    }
                }
            });

            page.navigate(ERP_ORDER_PAGE_URL);
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE);
            
            // Simple check: if URL contains "login" or title contains "登录"
            // Or if we are redirected away from the target URL (assuming target URL is protected)
            // The user said: "according to whether it is accessible"
            // Let's assume if we are NOT at the target URL, we are not logged in.
            // But sometimes URLs have query params. Let's check if path starts with expected.
            
            boolean isLoggedIn = isPageAccessible(page);
            
            if (!isLoggedIn) {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "检测到ERP系统未登录，请在打开的浏览器中完成登录，任务将等待您的操作。");
                
                // Wait loop
                long maxWaitTime = 5 * 60 * 1000; // 5 minutes
                long startTime = System.currentTimeMillis();
                
                while (!isLoggedIn) {
                    if (System.currentTimeMillis() - startTime > maxWaitTime) {
                        DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "登录等待超时，任务终止。");
                        return false;
                    }
                    
                    try {
                        Thread.sleep(1000); 

                        isLoggedIn = isPageAccessible(page);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return false;
                    } catch (Exception e) {
                        // Ignore navigation errors during wait (e.g. user interacting)
                    }
                }
                
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "ERP登录成功，继续执行任务。");
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            try {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "检查登录状态时出错: " + e.getMessage());
            } catch (Exception ex) { ex.printStackTrace(); }
            return false;
        }
    }
    
    private boolean isPageAccessible(Page page) {
        // 检查是否有登录状态的元素
        Locator userElement = null;
        
        try {
            userElement = page.frameLocator("iframe") 
                    .locator("//div[contains(text(),'全部订单')]").first();
                    
            userElement.waitFor(new Locator.WaitForOptions().setTimeout(1000));
        } catch (Exception e) {
            System.out.println("未检测到全部订单元素，判断为未登录。");
            return false;
        }

        return true;
    }

    private String fetchData(Page page, List<String> notifyUsers, String orderId) {
        try {
            // Construct payload
            JSONObject payload = new JSONObject();
            payload.put("ascOrDesc", false);
            payload.put("coId", "10533653");
            payload.put("dateQueryType", "OrderDate");
            payload.put("noteType", "NOFILTER");
            payload.put("oidList", Collections.singletonList(orderId));
            payload.put("orderByKey", 0);
            payload.put("orderTypeEnum", "ALL");
            payload.put("pageNum", 1);
            payload.put("pageSize", 50);
            payload.put("searchType", 1);
            payload.put("uid", "11449363");

            // Use APIRequestContext
            APIRequestContext request = page.context().request();
            
            // 1. Prepare Request Options
            RequestOptions options = RequestOptions.create()
                    .setData(payload)
                    .setHeader("Content-Type", "application/json")
                    .setHeader("Origin", "https://sc.scm121.com")
                    .setHeader("Referer", "https://sc.scm121.com/tradeManage/tower/distribute");

            // 2. Use Captured Headers from real network requests (Best Source of Truth)
            // But be careful about header size (HTTP 431)
            if (!capturedApiHeaders.isEmpty()) {
                System.out.println("Using Captured Headers: " + capturedApiHeaders.keySet()); // Log keys only for privacy
                for (Map.Entry<String, String> entry : capturedApiHeaders.entrySet()) {
                     String key = entry.getKey();
                     String lowerKey = key.toLowerCase();
                     
                     // Filter out standard/bulky headers that Playwright/Network stack will handle
                     // or that might cause conflicts/bloat
                     if (lowerKey.equals("content-length") || 
                         lowerKey.equals("host") || 
                         lowerKey.equals("connection") || 
                         lowerKey.equals("date") ||
                         lowerKey.equals("upgrade-insecure-requests") ||
                         lowerKey.equals("accept-encoding") ||
                         lowerKey.startsWith("sec-ch-ua") || // Remove browser hint headers which are verbose
                         lowerKey.startsWith(":")) { // HTTP/2 pseudo-headers
                         continue;
                     }
                     
                     options.setHeader(key, entry.getValue());
                }
            } else {
                // Fallback: Manually construct headers if capture failed
                
                // Get Cookies
                List<com.microsoft.playwright.options.Cookie> cookies = page.context().cookies();
                StringBuilder cookieHeader = new StringBuilder();
                for (com.microsoft.playwright.options.Cookie cookie : cookies) {
                    if (cookieHeader.length() > 0) cookieHeader.append("; ");
                    cookieHeader.append(cookie.name).append("=").append(cookie.value);
                }
                if (cookieHeader.length() > 0) {
                    options.setHeader("Cookie", cookieHeader.toString());
                }

                // Get Tokens from LocalStorage via script
                String storageScript = "() => {" +
                        "  let headers = {};" +
                        "  try {" +
                        "    for (let i = 0; i < localStorage.length; i++) {" +
                        "      let k = localStorage.key(i);" +
                        "      if (k.toLowerCase().includes('token') || k.toLowerCase().includes('auth') || k === 'uid') {" +
                        "         headers[k] = localStorage.getItem(k);" +
                        "      }" +
                        "    }" +
                        "    for (let i = 0; i < sessionStorage.length; i++) {" +
                        "      let k = sessionStorage.key(i);" +
                        "      if (k.toLowerCase().includes('token') || k.toLowerCase().includes('auth')) {" +
                        "         headers[k] = sessionStorage.getItem(k);" +
                        "      }" +
                        "    }" +
                        "  } catch (e) {}" +
                        "  return JSON.stringify(headers);" +
                        "}";
                String storageJson = (String) page.evaluate(storageScript);
                JSONObject storageHeaders = JSONObject.parseObject(storageJson);
                
                for (String key : storageHeaders.keySet()) {
                    String val = storageHeaders.getString(key);
                    options.setHeader(key, val);
                    if (key.contains("token") || key.contains("Token")) {
                         options.setHeader("token", val);
                    }
                }
            }

            APIResponse response = request.post(API_URL, options);
            int status = response.status();
            String body = response.text();
            
            JSONObject bodyJson = null;
            try {
                bodyJson = JSONObject.parseObject(body);
            } catch (Exception e) {
                try {
                    DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "接口返回非JSON数据 (Status " + status + "):\n" + body);
                } catch (Exception ex) { ex.printStackTrace(); }
                return "Error: Non-JSON response (Status " + status + ")";
            }

            return handleApiResponse(notifyUsers, status, bodyJson);

        } catch (Exception e) {
            e.printStackTrace();
            try {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "获取数据失败: " + e.getMessage());
            } catch (Exception ex) { ex.printStackTrace(); }
            return "Error: " + e.getMessage();
        }
    }

    private String handleApiResponse(List<String> notifyUsers, int status, JSONObject bodyJson) {
        try {
            if (status == 200) {
                boolean success = bodyJson.getBooleanValue("success");
                if (success) {
                    Object data = bodyJson.get("data");
                    if (data != null) {
                        StringBuilder resultBuilder = new StringBuilder();
                        int count = 0;

                        if (data instanceof JSONArray) {
                            JSONArray dataArray = (JSONArray) data;
                            if (dataArray.isEmpty()) {
                                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "查询成功，但未找到匹配的订单记录 (data is empty)。");
                                return "No records found";
                            }
                            count = dataArray.size();
                            for (int i = 0; i < dataArray.size(); i++) {
                                if (i > 0) resultBuilder.append("\n----------------------------------------\n");
                                resultBuilder.append(formatOrderInfo(dataArray.getJSONObject(i)));
                            }
                        } else if (data instanceof JSONObject) {
                            count = 1;
                            resultBuilder.append(formatOrderInfo((JSONObject) data));
                        } else {
                             // Fallback for unexpected type
                             String displayData = JSON.toJSONString(data, JSONWriter.Feature.PrettyFormat);
                             if (displayData.length() > 2000) {
                                  displayData = displayData.substring(0, 2000) + "\n...(truncated)";
                             }
                             DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "查询成功 (未知数据结构):\n" + displayData);
                             return "Unknown data structure: " + displayData;
                        }

                        String finalOutput = resultBuilder.toString();
                        // Truncate if too long for DingTalk (limit usually ~2000-4000 chars, keeping safe)
                        if (finalOutput.length() > 3500) {
                             finalOutput = finalOutput.substring(0, 3500) + "\n...(内容过长已截断)";
                        }
                        
                        DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "查询成功，找到 " + count + " 条记录:\n" + finalOutput);
                        return finalOutput;

                    } else {
                        DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "查询成功，但返回数据为空 (data is null)。");
                        return "Data is null";
                    }
                } else {
                    String msg = bodyJson.getString("message");
                    if ("empty token".equals(msg)) {
                        DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "查询失败: 认证Token丢失 (empty token)。\n建议：请在浏览器中刷新页面或重新登录。");
                    } else {
                        DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "查询失败 (Business Error): " + msg);
                    }
                    return "Error: " + msg;
                }
            } else {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "接口调用失败 (Status " + status + "):\n" + bodyJson.toJSONString());
                return "Error: Status " + status;
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "处理响应结果失败: " + e.getMessage());
            } catch (Exception ex) { ex.printStackTrace(); }
            return "Error: " + e.getMessage();
        }
    }

    private String formatOrderInfo(JSONObject order) {
        if (order == null) return "";
        StringBuilder sb = new StringBuilder();
        
        // Basic Fields
        sb.append("订单ID: ").append(order.getString("oid")).append("\n");
        sb.append("平台: ").append(order.getString("shopSite")).append("\n");
        sb.append("店铺名称: ").append(order.getString("shopName")).append("\n");
        sb.append("供应商名称: ").append(order.getString("supplierName")).append("\n");
        sb.append("订单状态: ").append(order.getString("orderStatus")).append("\n");
        sb.append("订单类型: ").append(order.getString("orderType")).append("\n");
        sb.append("订单标签: ").append(order.getString("labels")).append("\n");
        sb.append("客户下单金额: ").append(order.getString("paidAmount")).append("\n");
        sb.append("分销采购金额: ").append(order.getString("drpAmount")).append("\n");
        sb.append("订单商品数量: ").append(order.getString("goodsQty")).append("\n");
        sb.append("订单支付时间: ").append(order.getString("payTime")).append("\n");
        sb.append("物流发货时间: ").append(order.getString("deliveryDate")).append("\n");
        sb.append("快递公司: ").append(order.getString("expressCompany")).append("\n");
        sb.append("物流单号: ").append(order.getString("trackNo")).append("\n");

        // Goods List
        JSONArray goodsList = order.getJSONArray("disInnerOrderGoodsViewList");
        if (goodsList != null && !goodsList.isEmpty()) {
            sb.append("商品列表:\n");
            for (int i = 0; i < goodsList.size(); i++) {
                JSONObject item = goodsList.getJSONObject(i);
                sb.append("  - 商品名称: ").append(item.getString("itemName")).append("\n");
                sb.append("    商品数量: ").append(item.getString("itemCount")).append("\n");
                sb.append("    商品规格: ").append(item.getString("properties")).append("\n");
                sb.append("    总价: ").append(item.getString("totalPrice")).append("\n");
            }
        } else {
            sb.append("商品列表: (空)\n");
        }

        return sb.toString();
    }
}
