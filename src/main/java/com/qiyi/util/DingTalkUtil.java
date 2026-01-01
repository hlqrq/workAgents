package com.qiyi.util;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dingtalk.api.DefaultDingTalkClient;
import com.dingtalk.api.DingTalkClient;
import com.dingtalk.api.request.OapiRobotSendRequest;
import com.dingtalk.api.request.OapiUserListidRequest;
import com.dingtalk.api.request.OapiUserListsimpleRequest;
import com.dingtalk.api.request.OapiV2DepartmentListsubRequest;
import com.dingtalk.api.response.OapiRobotSendResponse;
import com.dingtalk.api.response.OapiUserListidResponse;
import com.dingtalk.api.response.OapiUserListsimpleResponse;
import com.dingtalk.api.response.OapiV2DepartmentListsubResponse;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.callback.OpenDingTalkCallbackListener;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qiyi.dingtalk.DingTalkDepartment;
import com.qiyi.dingtalk.DingTalkUser;
import com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenResponse;
import com.aliyun.dingtalkrobot_1_0.models.*;
import com.aliyun.tea.TeaException;

import org.apache.commons.codec.binary.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;

public class DingTalkUtil {

    // 机器人配置信息（建议放在配置文件中）
    private static final String ROBOT_TOKEN = "24144058d4cd5c936cd00b0df90bceb5d675869442d948d855898e90e480890d";
    private static final String ROBOT_SECRET = "SEC44e2bf5873b1445bd536d0ed46324a601338542ff2fcf9f7729d183fba9defe3";

    private static final String ROBOT_CLIENT_ID = "dinga0isowzajgy9g4d4";
    private static final String ROBOT_CLIENT_SECRET = "kgR27JbC0plMISfb0ul812LU8OA6PMnYGAEKXy2zpVY_V5Mky6sfTk6Qki5dgMpI";

    private static final String ROBOT_CODE = "dinga0isowzajgy9g4d4";

    public static void main(String[] args) throws Exception {
        DingTalkUtil dingTalkUtil = new DingTalkUtil();

        // 获取所有部门列表
        List<DingTalkDepartment> allDepartments = dingTalkUtil.getAllDepartments(ROBOT_CLIENT_ID, ROBOT_CLIENT_SECRET,true,true);
        System.out.println("获取到部门总数: " + allDepartments.size());
        for (DingTalkDepartment dept : allDepartments) {
            System.out.println("部门: " + dept.getName() + ", ID: " + dept.getDeptId() + ", 用户数量: " + dept.getUserList().size());

            for(DingTalkUser user : dept.getUserList())
            {
                System.out.println("用户: " + user.getName() + ", ID: " + user.getUserid());
            }
        }

        DingTalkUser user = dingTalkUtil.findUserFromDepartmentByName("岑文初");
        System.out.println("用户: " + user.getName() + ", ID: " + user.getUserid());


        dingTalkUtil.startRobotMsgCallbackConsumer(ROBOT_CLIENT_ID,ROBOT_CLIENT_SECRET);

        //0.发送文本给单用户
        dingTalkUtil.sendTextMessageToEmployees(ROBOT_CLIENT_ID,ROBOT_CLIENT_SECRET,ROBOT_CODE,Arrays.asList(user.getUserid()),"发送单聊测试消息！");

        // 1. 发送文本消息
        dingTalkUtil.sendTextMessageToGroup("测试文本消息：钉钉，让进步发生", Arrays.asList(user.getUserid()), false);

        // 2. 发送 Link 消息 (带图片)
        dingTalkUtil.sendLinkMessageToGroup(
                "时代的火车向前开",
                "这个即将发布的新版本，创始人xx称它为红树林。",
                "https://www.dingtalk.com/",
                "https://img.alicdn.com/tfs/TB1NwmBEL9TBuNjy1zbXXXpepXa-2400-1218.png"
        );

        // 3. 发送 Markdown 消息 (支持正文插入图片)
        String markdownText = "#### 杭州天气 \n" +
                "> 9度，西北风1级，空气良89，相对温度73%\n\n" +
                "> ![screenshot](https://img.alicdn.com/tfs/TB1NwmBEL9TBuNjy1zbXXXpepXa-2400-1218.png)\n" +
                "> ###### 10点20分发布 [天气](https://www.dingtalk.com/) \n";
        dingTalkUtil.sendMarkdownMessageToGroup("杭州天气", markdownText, Arrays.asList(user.getUserid()), false);
        
        // 4. 发送 ActionCard (独立跳转卡片)
        dingTalkUtil.sendActionCardMessageToGroup(
                "乔布斯 20 年前想打造一间苹果咖啡厅",
                "![screenshot](https://img.alicdn.com/tfs/TB1NwmBEL9TBuNjy1zbXXXpepXa-2400-1218.png) \n\n ### 乔布斯 20 年前想打造的苹果咖啡厅 \n\n Apple Store 的设计正从原来满满的科技感走向生活化，而其生活化的走向其实可以追溯到 20 年前苹果一个建立咖啡馆的计划",
                "阅读全文",
                "https://www.dingtalk.com/"
        );

        //交互的读取控制台消息，来判断是否要暂停机器人消息回调
        // dingTalkUtil.stopRobotMsgCallbackConsumer();

        System.out.println("\n机器人监听已启动。在控制台输入 'exit' 并回车以停止程序...");
        java.util.Scanner scanner = new java.util.Scanner(System.in);
        while (true) {
            if (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) {
                    dingTalkUtil.stopRobotMsgCallbackConsumer();
                    System.out.println("程序已退出。");
                    break;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        scanner.close();
        System.exit(0);
    }


    private OpenDingTalkClient streamClient;

    //启动监听机器人消息回调本地线程，搭配RobotMsgCallbackConsumer来使用
    //注意：这里是阻塞线程，需要在单独的线程中调用
    public void startRobotMsgCallbackConsumer(String clientId,String clientSecret) {
        if (streamClient != null) {
            System.out.println("Robot callback consumer is already running.");
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                streamClient = OpenDingTalkStreamClientBuilder
                        .custom()
                        .credential(new AuthClientCredential(clientId, clientSecret))
                        .registerCallbackListener("/v1.0/im/bot/messages/get", new RobotMsgCallbackConsumer())
                        .build();

                System.out.println("DingTalk Stream Client starting...");
                streamClient.start();
            } catch (Exception e) {
                System.err.println("DingTalk Stream Client Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // 设置为非守护线程，确保主线程结束后该线程继续运行 (resident)
        thread.setDaemon(false); 
        thread.setName("DingTalk-Robot-Consumer-Thread");
        thread.start();
    }

    // 显示调用关闭函数
    public void stopRobotMsgCallbackConsumer() {
        if (streamClient != null) {
            try {
                streamClient.stop();
                System.out.println("DingTalk Stream Client stopped.");
            } catch (Exception e) {
                e.printStackTrace();
            }
            streamClient = null;
        }
    }

    //接收企业内部机器人消息，在群里如果有人 @机器人，就会收到消息
     public static class RobotMsgCallbackConsumer implements OpenDingTalkCallbackListener<JSONObject, JSONObject> {
    /*
        * @param request
        * @return
        */
        @Override
        public JSONObject execute(JSONObject request) {
            System.out.println(JSON.toJSONString(request));
            try {
                JSONObject text = request.getJSONObject("text");
                if (text != null) {
                    //机器人接收消息内容
                    String msg = text.getString("content").trim();
                    String openConversationId = request.getString("conversationId");

                    // 做一些业务处理，实现对于群聊中的功能响应
                    
                }
            } catch (Exception e) {
                System.out.println("receive group message by robot error:" +e.getMessage());
            }
            return new JSONObject();
        }
    } 


    public static com.aliyun.dingtalkrobot_1_0.Client createClient() throws Exception {
        Config config = new Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkrobot_1_0.Client(config);
    }

    public static com.aliyun.dingtalkoauth2_1_0.Client createOAuthClient() throws Exception {
        com.aliyun.teaopenapi.models.Config config = new com.aliyun.teaopenapi.models.Config();
        config.protocol = "https";
        config.regionId = "central";
        return new com.aliyun.dingtalkoauth2_1_0.Client(config);
    }

    // =========================================================================
    // 缓存支持
    // =========================================================================

    public interface DingTalkCache {
        String get(String key);
        void put(String key, String value, long expireSeconds);
    }

    public static class LocalDingTalkCache implements DingTalkCache {
        private static final java.util.Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();
        private static final java.util.Map<String, Long> expireMap = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String get(String key) {
            Long expireTime = expireMap.get(key);
            if (expireTime != null && expireTime > System.currentTimeMillis()) {
                return cache.get(key);
            } else if (expireTime != null) {
                // Expired, clean up
                cache.remove(key);
                expireMap.remove(key);
            }
            return null;
        }

        @Override
        public void put(String key, String value, long expireSeconds) {
            cache.put(key, value);
            expireMap.put(key, System.currentTimeMillis() + expireSeconds * 1000);
        }
    }

    // 默认使用本地缓存，未来可替换为 Redis 等集中式缓存实现
    private static DingTalkCache dingTalkCache = new LocalDingTalkCache();

    public static void setDingTalkCache(DingTalkCache cache) {
        dingTalkCache = cache;
    }

    public String getDingTalkRobotAccessToken(String appKey,String appSecret) throws Exception
    {
        // 尝试从缓存获取
        String cacheKey = "dingtalk_access_token_" + appKey;
        String cachedToken = dingTalkCache.get(cacheKey);
        if (cachedToken != null) {
            // System.out.println("Get accessToken from cache: " + cachedToken);
            return cachedToken;
        }

        GetAccessTokenResponse accessTokenResponse = null;
        String accessToken = null;
        
        com.aliyun.dingtalkoauth2_1_0.Client client = DingTalkUtil.createOAuthClient();
        com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest getAccessTokenRequest = new com.aliyun.dingtalkoauth2_1_0.models.GetAccessTokenRequest()
                .setAppKey(appKey)
                .setAppSecret(appSecret);
        try {
            accessTokenResponse = client.getAccessToken(getAccessTokenRequest);
        } catch (TeaException err) {
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
            }

        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            if (!com.aliyun.teautil.Common.empty(err.code) && !com.aliyun.teautil.Common.empty(err.message)) {
                // err 中含有 code 和 message 属性，可帮助开发定位问题
            }

        } 
        if (accessTokenResponse != null) {
            accessToken = accessTokenResponse.getBody().getAccessToken();
            System.out.println("accessTokenResponse: " + accessToken);
            
            // 获取成功后放入缓存，设置过期时间为 60 分钟 (3600 秒)
            if (accessToken != null && !accessToken.isEmpty()) {
                dingTalkCache.put(cacheKey, accessToken, 3600);
            }
        }

        return accessToken;
    }

    // =========================================================================
    // 下面是针对常用消息类型的封装方法
    // =========================================================================

    /**
     * 简单的辅助方法：处理JSON特殊字符转义 (简单实现，建议使用 Jackson/Gson 替代)
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    /**
     * 分页获取钉钉API数据
     * @param appKey 应用Key
     * @param appSecret 应用密钥
     * @param departmentId 部门ID
     * @param pageSize 每页大小
     * @param dataExtractor 数据提取函数
     * @return 所有数据列表
     */
    public <T> List<T> getAllDataByPagination(
            String appKey, 
            String appSecret, 
            String departmentId,
            int pageSize,
            Function<Long, List<T>> dataExtractor) throws Exception {
        
        List<T> allData = new ArrayList<>();
        long cursor = 0;
        int page = 1;
        boolean hasMore = true;
        
        while (hasMore) {
            try {
                List<T> pageData = dataExtractor.apply(cursor);
                
                if (pageData != null && !pageData.isEmpty()) {
                    allData.addAll(pageData);
                    System.out.printf("第 %d 页获取 %d 条数据，累计 %d 条%n", 
                        page, pageData.size(), allData.size());
                    
                    // 如果返回的数据小于请求的页大小，说明没有更多数据了
                    if (pageData.size() < pageSize) {
                        hasMore = false;
                    } else {
                        cursor += pageSize;
                        page++;
                    }
                } else {
                    hasMore = false;
                }
                
                // 避免频繁请求
                Thread.sleep(200);
                
            } catch (Exception e) {
                System.err.println("分页获取数据失败: " + e.getMessage());
                throw e;
            }
        }
        
        return allData;
    }
    
    /**
     * 获取部门下所有用户的通用方法
     */
    public List<DingTalkUser> getAllUsersInDepartment(String appKey, String appSecret, String departmentId) throws Exception {

        String accessToken = getDingTalkRobotAccessToken(appKey, appSecret);
        if (accessToken == null || accessToken.isEmpty()) {
            System.out.println("Failed to get access token");
            return Collections.emptyList();
        }
        
        return getAllDataByPagination(appKey, appSecret, departmentId, 30, cursor -> {
            try {
                DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/user/listsimple");
                OapiUserListsimpleRequest req = new OapiUserListsimpleRequest();
                req.setDeptId(Long.parseLong(departmentId));
                req.setCursor(cursor);
                req.setSize(30L);
                req.setOrderField("modify_desc");
                req.setContainAccessLimit(false);
                req.setLanguage("zh_CN");
                
                OapiUserListsimpleResponse rsp = client.execute(req, accessToken);
                if (rsp.isSuccess() && rsp.getResult() != null) {
                    return rsp.getResult().getList().stream()
                            .map(user -> new DingTalkUser(user.getName(), user.getUserid()))
                            .collect(Collectors.toList());
                }
            } catch (Exception e) {
                throw new RuntimeException("获取用户数据失败", e);
            }
            return Collections.emptyList();
        });
    }


    public List<DingTalkDepartment> getDingTalkDepartmentList(String appKey, String appSecret,String departmentId) throws Exception {

        List<DingTalkDepartment> departmentList = new ArrayList<DingTalkDepartment>();

        String accessToken = getDingTalkRobotAccessToken(appKey, appSecret);
        if (accessToken == null || accessToken.isEmpty()) {
            System.out.println("Failed to get access token");
            return departmentList;
        }

        DingTalkClient client = new DefaultDingTalkClient("https://oapi.dingtalk.com/topapi/v2/department/listsub");
        OapiV2DepartmentListsubRequest req = new OapiV2DepartmentListsubRequest();

        if (departmentId!= null)
        {
            req.setDeptId(Long.parseLong(departmentId));
        }

        OapiV2DepartmentListsubResponse rsp = client.execute(req, accessToken);


        if (rsp != null && rsp.getBody() != null) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(rsp.getBody().toString());
            
            // 检查错误码
            if (root.get("errcode").asInt() != 0) {
                throw new RuntimeException("API返回错误: " + root.get("errmsg").asText());
            }
            
            // 解析result数组
            JsonNode resultArray = root.get("result");
            
            for (JsonNode node : resultArray) {
                DingTalkDepartment dept = new DingTalkDepartment();
                dept.setDeptId(node.get("dept_id").asText());
                dept.setName(node.get("name").asText());
                dept.setParentId(node.get("parent_id").asText());
                departmentList.add(dept);
            }
        }

        return  departmentList;
    }


    /**
     * 获取公司所有部门列表（从根部门 ID 1 开始递归遍历），支持缓存指定时间
     * @param cacheSeconds 缓存时间（秒），如果 <= 0 则不使用缓存
     */
    public List<DingTalkDepartment> getAllDepartments(String appKey, String appSecret, boolean isNeedUserList, boolean needCache) throws Exception {
        String cacheKey = "dingtalk_departments_v2_" + appKey + "_" + isNeedUserList;
        
        if (needCache) {
            // 尝试从缓存获取
            String cachedData = dingTalkCache.get(cacheKey);
            if (cachedData != null && !cachedData.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    return mapper.readValue(cachedData, new com.fasterxml.jackson.core.type.TypeReference<List<DingTalkDepartment>>(){});
                } catch (Exception e) {
                    // 如果反序列化失败，忽略缓存错误，继续执行重新获取逻辑
                }
            }
        }

        List<DingTalkDepartment> allDepartments = new ArrayList<>();

        allDepartments.add(new DingTalkDepartment("1","根部门","0"));

        // 递归获取所有部门，从根部门 ID 1 开始
        traverseDepartments(appKey, appSecret, null, allDepartments);

        if (isNeedUserList)
        {
            // 为每个部门添加用户列表
            for (DingTalkDepartment dept : allDepartments) {
                dept.setUserList(getAllUsersInDepartment(appKey, appSecret, dept.getDeptId()));
            }
        }
        
        if (needCache) {
            // 存入缓存
            try {
                ObjectMapper mapper = new ObjectMapper();
                String jsonStr = mapper.writeValueAsString(allDepartments);
                dingTalkCache.put(cacheKey, jsonStr, 30*60);
            } catch (Exception e) {
                System.err.println("Failed to serialize departments for cache: " + e.getMessage());
            }
        }
        
        return allDepartments;
    }

    private void traverseDepartments(String appKey, String appSecret, String parentId, List<DingTalkDepartment> result) throws Exception {
        // 获取当前部门的子部门列表
        List<DingTalkDepartment> children = getDingTalkDepartmentList(appKey, appSecret, parentId);
        
        if (children != null && !children.isEmpty()) {
            result.addAll(children);
            // 对每个子部门，继续递归获取其子部门
            for (DingTalkDepartment child : children) {
                traverseDepartments(appKey, appSecret, child.getDeptId(), result);
            }
        }
    }

    public DingTalkUser findUserFromDepartmentByName(String name) throws Exception {
        List<DingTalkDepartment> allDepartments = getAllDepartments(ROBOT_CLIENT_ID, ROBOT_CLIENT_SECRET, true, true);
        
        for (DingTalkDepartment dept : allDepartments) {

            if (dept.getUserList() != null && !dept.getUserList().isEmpty())
            {
                for (DingTalkUser user : dept.getUserList()) {
                    if (user.getName().equals(name)) {
                        return user;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 1. 发送【文本消息】
     * 模板Key: sampleText
     */
    public boolean sendTextMessageToEmployees(String appKey, String appSecret, String robotCode, List<String> userIds, String content) throws Exception {
        String msgKey = "sampleText";
        // 构造JSON: {"content": "具体的文本内容"}
        String msgParam = String.format("{\"content\": \"%s\"}", escapeJson(content));
        return sendBatchMessageToEmployees(appKey, appSecret, robotCode, userIds, msgKey, msgParam);
    }

    /**
     * 2. 发送【图片消息】
     * 模板Key: sampleImageMsg
     * 注意: photoURL 必须是公网可访问的图片链接
     */
    public boolean sendImageMessageToEmployees(String appKey, String appSecret, String robotCode, List<String> userIds, String photoUrl) throws Exception {
        String msgKey = "sampleImageMsg";
        // 构造JSON: {"photoURL": "https://..."}
        String msgParam = String.format("{\"photoURL\": \"%s\"}", photoUrl);
        return sendBatchMessageToEmployees(appKey, appSecret, robotCode, userIds, msgKey, msgParam);
    }

    /**
     * 3. 发送【Markdown消息】
     * 模板Key: sampleMarkdown
     */
    public boolean sendMarkdownMessageToEmployees(String appKey, String appSecret, String robotCode, List<String> userIds, String title, String markdownText) throws Exception {
        String msgKey = "sampleMarkdown";
        // 构造JSON: {"title": "标题", "text": "markdown内容"}
        // 注意：实际生产中请使用 JSON 库 (如 Gson/Jackson) 来避免拼接字符串时的转义问题
        String msgParam = String.format("{\"title\": \"%s\", \"text\": \"%s\"}", escapeJson(title), escapeJson(markdownText));
        return sendBatchMessageToEmployees(appKey, appSecret, robotCode, userIds, msgKey, msgParam);
    }

    /**
     * 4. 发送【链接消息】
     * 模板Key: sampleLink
     */
    public boolean sendLinkMessageToEmployees(String appKey, String appSecret, String robotCode, List<String> userIds, 
                                   String title, String text, String messageUrl, String picUrl) throws Exception {
        String msgKey = "sampleLink";
        // 构造JSON
        String msgParam = String.format(
            "{\"title\": \"%s\", \"text\": \"%s\", \"messageUrl\": \"%s\", \"picUrl\": \"%s\"}",
            escapeJson(title), escapeJson(text), messageUrl, picUrl
        );
        return sendBatchMessageToEmployees(appKey, appSecret, robotCode, userIds, msgKey, msgParam);
    }
    
    /**
     * 5. 发送【ActionCard 卡片消息】
     * 模板Key: sampleActionCard
     */
    public boolean sendActionCardMessageToEmployees(String appKey, String appSecret, String robotCode, List<String> userIds, 
                                         String title, String text, String singleTitle, String singleUrl) throws Exception {
        String msgKey = "sampleActionCard";
        // 构造JSON: 支持 markdown 格式的 text
        String msgParam = String.format(
            "{\"title\": \"%s\", \"text\": \"%s\", \"singleTitle\": \"%s\", \"singleURL\": \"%s\"}",
            escapeJson(title), escapeJson(text), escapeJson(singleTitle), singleUrl
        );
        return sendBatchMessageToEmployees(appKey, appSecret, robotCode, userIds, msgKey, msgParam);
    }


    /**
     * 【核心通用方法】发送单聊消息
     * 文档: https://open.dingtalk.com/document/dingstart/types-of-messages-sent-by-robots
     *
     * @param appKey    应用AppKey
     * @param appSecret 应用AppSecret
     * @param robotCode 机器人编码 (通常在开发者后台可以找到)
     * @param userIds   接收人的UserId列表
     * @param msgKey    消息模板Key (如 sampleText, sampleMarkdown, sampleImageMsg 等)
     * @param msgParam  消息模板参数 (JSON字符串)
     */
    public boolean sendBatchMessageToEmployees (String appKey, String appSecret, String robotCode, List<String> userIds, String msgKey, String msgParam) throws Exception {
        boolean result = false;
        
        // 假设 DingTalkUtil.createClient() 已经实现了 client 初始化
        com.aliyun.dingtalkrobot_1_0.Client client = DingTalkUtil.createClient();
        
        BatchSendOTOHeaders batchSendOTOHeaders = new BatchSendOTOHeaders();
        // 假设 getDingTalkRobotAccessToken 是你已有的获取 token 方法
        batchSendOTOHeaders.xAcsDingtalkAccessToken = getDingTalkRobotAccessToken(appKey, appSecret);

        BatchSendOTORequest batchSendOTORequest = new BatchSendOTORequest()
                .setRobotCode(robotCode)
                .setUserIds(userIds)
                .setMsgKey(msgKey)      // 动态设置模板Key
                .setMsgParam(msgParam); // 动态设置参数

        try {
            client.batchSendOTOWithOptions(batchSendOTORequest, batchSendOTOHeaders, new RuntimeOptions());
            result = true;
            System.out.println("发送成功: " + msgKey);
        } catch (TeaException err) {
            System.err.println("发送失败 Code: " + err.code);
            System.err.println("发送失败 Message: " + err.message);
        } catch (Exception _err) {
            TeaException err = new TeaException(_err.getMessage(), _err);
            System.err.println("系统异常: " + err.message);
        }

        return result;
    }

    /**
     * 发送文本消息 (Text)
     */
    public boolean sendTextMessageToGroup(String content, List<String> atUserIds, boolean isAtAll) {
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("text");
        
        OapiRobotSendRequest.Text text = new OapiRobotSendRequest.Text();
        text.setContent(content);
        request.setText(text);

        return sendRobotMessageToGroup(request, atUserIds, isAtAll);
    }

    /**
     * 发送链接消息 (Link) - 支持封面图
     */
    public boolean sendLinkMessageToGroup(String title, String text, String messageUrl, String picUrl) {
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("link");
        
        OapiRobotSendRequest.Link link = new OapiRobotSendRequest.Link();
        link.setTitle(title);
        link.setText(text);
        link.setMessageUrl(messageUrl);
        link.setPicUrl(picUrl); // 这里的图片链接
        request.setLink(link);

        return sendRobotMessageToGroup(request, null, false); // Link类型不支持@
    }

    /**
     * 发送 Markdown 消息 - 推荐用于发送带图详情
     * 图片语法: ![alt](url)
     */
    public boolean sendMarkdownMessageToGroup(String title, String markdownText, List<String> atUserIds, boolean isAtAll) {
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("markdown");
        
        OapiRobotSendRequest.Markdown markdown = new OapiRobotSendRequest.Markdown();
        markdown.setTitle(title); // 首屏会话透出的展示内容
        markdown.setText(markdownText);
        request.setMarkdown(markdown);

        return sendRobotMessageToGroup(request, atUserIds, isAtAll);
    }

    /**
     * 发送 ActionCard (整体跳转)
     */
    public boolean sendActionCardMessageToGroup(String title, String markdownText, String btnTitle, String btnUrl) {
        OapiRobotSendRequest request = new OapiRobotSendRequest();
        request.setMsgtype("actionCard");
        
        OapiRobotSendRequest.Actioncard actionCard = new OapiRobotSendRequest.Actioncard();
        actionCard.setTitle(title);
        actionCard.setText(markdownText);
        actionCard.setBtnOrientation("0"); // 0-按钮竖直排列，1-横向排列
        actionCard.setSingleTitle(btnTitle);
        actionCard.setSingleURL(btnUrl);
        request.setActionCard(actionCard);

        return sendRobotMessageToGroup(request, null, false);
    }

    /**
     * 核心发送逻辑：签名、设置@对象、执行发送
     */
    private boolean sendRobotMessageToGroup(OapiRobotSendRequest request, List<String> atUserIds, boolean isAtAll) {
        try {
            Long timestamp = System.currentTimeMillis();
            String stringToSign = timestamp + "\n" + ROBOT_SECRET;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(ROBOT_SECRET.getBytes("UTF-8"), "HmacSHA256"));
            byte[] signData = mac.doFinal(stringToSign.getBytes("UTF-8"));
            String sign = URLEncoder.encode(new String(Base64.encodeBase64(signData)), "UTF-8");

            String serverUrl = "https://oapi.dingtalk.com/robot/send?sign=" + sign + "&timestamp=" + timestamp;
            DingTalkClient client = new DefaultDingTalkClient(serverUrl);

            // 处理 @ 逻辑
            if (atUserIds != null || isAtAll) {
                OapiRobotSendRequest.At at = new OapiRobotSendRequest.At();
                if (atUserIds != null && !atUserIds.isEmpty()) {
                    at.setAtUserIds(atUserIds);
                }
                at.setIsAtAll(isAtAll);
                request.setAt(at);
            }

            OapiRobotSendResponse rsp = client.execute(request, ROBOT_TOKEN);
            System.out.println("Result: " + rsp.getBody());
            return rsp.isSuccess();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}