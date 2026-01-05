package com.qiyi.tools;

import com.alibaba.fastjson2.JSONObject;
import java.util.List;

public interface Tool {
    String getName();
    String getDescription();
    void execute(JSONObject params, String senderId, List<String> atUserIds);
}
