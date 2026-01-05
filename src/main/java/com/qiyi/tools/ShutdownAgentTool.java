package com.qiyi.tools;

import com.alibaba.fastjson2.JSONObject;
import com.qiyi.util.DingTalkUtil;
import java.util.List;
import java.util.ArrayList;

public class ShutdownAgentTool implements Tool {
    @Override
    public String getName() {
        return "shutdown_agent";
    }

    @Override
    public String getDescription() {
        return "关闭钉钉机器人服务并退出 DingTalkAgent。Parameters: none.";
    }

    @Override
    public void execute(JSONObject params, String senderId, List<String> atUserIds) {
        List<String> notifyUsers = new ArrayList<>();
        if (senderId != null) notifyUsers.add(senderId);
        if (atUserIds != null && !atUserIds.isEmpty()) {
            notifyUsers.addAll(atUserIds);
        }
        try {
            DingTalkUtil.sendTextMessageToEmployees(notifyUsers, "已收到关闭指令，正在关闭钉钉机器人服务...");
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            DingTalkUtil.stopRobotMsgCallbackConsumer();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        System.exit(0);
    }
}
