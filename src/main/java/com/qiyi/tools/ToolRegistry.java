package com.qiyi.tools;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ToolRegistry {
    private static final Map<String, Tool> tools = new HashMap<>();

    public static void register(Tool tool) {
        tools.put(tool.getName(), tool);
    }

    public static Tool get(String name) {
        return tools.get(name);
    }

    public static Collection<Tool> getAll() {
        return tools.values();
    }
    
    public static boolean contains(String name) {
        return tools.containsKey(name);
    }
}
